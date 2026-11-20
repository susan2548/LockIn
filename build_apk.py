"""Build and locally sign Pordee with the Android SDK and JDK, offline."""

import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parent
APP = ROOT / "app" / "src" / "main"


def run(*command, env=None):
    subprocess.run([str(value) for value in command], check=True, env=env)


def find_sdk():
    candidates = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    candidates.extend([Path.home() / "AppData/Local/Android/Sdk",
                       Path.home() / "Library/Android/sdk", Path.home() / "Android/Sdk"])
    for value in candidates:
        if value and (Path(value) / "platforms").is_dir():
            return Path(value)
    raise SystemExit("Set ANDROID_HOME to an installed Android SDK.")


def get_signer(work):
    custom_key = os.environ.get("STUDYPACE_KEYSTORE")
    if custom_key:
        if not os.environ.get("STUDYPACE_STORE_PASS"):
            raise SystemExit("Set STUDYPACE_STORE_PASS when supplying STUDYPACE_KEYSTORE.")
        return Path(custom_key), os.environ.get("STUDYPACE_KEY_ALIAS", "studypace"), os.environ.copy()
    signing = work / "signing"
    signing.mkdir(exist_ok=True)
    key = signing / "local.p12"
    password_path = signing / "password"
    if not password_path.exists():
        if key.exists():
            raise SystemExit("Local signing password is missing. Restore it before building an update.")
        password_path.write_text(secrets.token_urlsafe(32), encoding="ascii")
        password_path.chmod(0o600)
    env = dict(os.environ, STUDYPACE_STORE_PASS=password_path.read_text(encoding="ascii").strip())
    if not key.exists():
        run("keytool", "-genkeypair", "-keystore", key, "-storetype", "PKCS12",
            "-storepass:env", "STUDYPACE_STORE_PASS", "-keypass:env", "STUDYPACE_STORE_PASS",
            "-alias", "studypace", "-keyalg", "RSA", "-keysize", "3072", "-validity",
            "9125", "-dname", "CN=StudyPace Local Build", env=env)
        key.chmod(0o600)
    return key, "studypace", env


def build(out, api, tools_version):
    sdk = find_sdk()
    build_tools = sdk / "build-tools" / tools_version
    jar = sdk / "platforms" / f"android-{api}" / "android.jar"
    if not jar.exists() or not build_tools.is_dir():
        raise SystemExit(f"Install Android API {api} and build-tools {tools_version}.")
    suffix = ".exe" if os.name == "nt" else ""
    aapt = build_tools / ("aapt2" + suffix)
    align = build_tools / ("zipalign" + suffix)
    signer = build_tools / "lib/apksigner.jar"
    work = ROOT / "work"
    work.mkdir(exist_ok=True)
    out.parent.mkdir(parents=True, exist_ok=True)
    key, alias, env = get_signer(work)
    with tempfile.TemporaryDirectory(prefix="apk-", dir=work) as temp:
        tmp = Path(temp)
        generated = tmp / "gen"
        compiled = tmp / "classes"
        dex = tmp / "dex"
        for folder in [generated, compiled, dex]:
            folder.mkdir()
        resources = []
        if (APP / "res").exists() and any((APP / "res").rglob("*.*")):
            resources = [tmp / "resources.zip"]
            run(aapt, "compile", "--dir", APP / "res", "-o", *resources)
        unsigned = tmp / "resources.apk"
        run(aapt, "link", "-I", jar, "--manifest", APP / "AndroidManifest.xml",
            "--java", generated, "-A", APP / "assets", "-o", unsigned, *resources)
        src = sorted((APP / "java").rglob("*.java")) + list(generated.rglob("*.java"))
        bootclasspath = os.pathsep.join(map(str, (build_tools / "core-lambda-stubs.jar", jar)))
        run("javac", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options",
            "-bootclasspath", bootclasspath, "-d", compiled, *src)
        classes = tmp / "classes.jar"
        run("jar", "cf", classes, "-C", compiled, ".")
        run("java", "-cp", build_tools / "lib/d8.jar", "com.android.tools.r8.D8",
            "--release", "--min-api", "26", "--lib", jar, "--output", dex, classes)
        with zipfile.ZipFile(unsigned, "a") as archive:
            for file in sorted(dex.glob("*.dex")):
                archive.write(file, arcname=file.name, compress_type=zipfile.ZIP_STORED)
        aligned = tmp / "aligned.apk"
        signed = tmp / "StudyPace.apk"
        run(align, "-f", "4", unsigned, aligned)
        run("java", "-jar", signer, "sign", "--ks", key, "--ks-key-alias", alias,
            "--ks-pass", "env:STUDYPACE_STORE_PASS", "--v4-signing-enabled", "false",
            "--out", signed, aligned, env=env)
        run("java", "-jar", signer, "verify", "--verbose", "--print-certs", signed)
        run(align, "-c", "4", signed)
        # The recipient may already have the previous build open for download.
        # Move an entirely written sibling into place after verification.
        with tempfile.NamedTemporaryFile(dir=out.parent, delete=False) as pending:
            pending_path = Path(pending.name)
            try:
                with signed.open("rb") as src_file:
                    shutil.copyfileobj(src_file, pending)
            except BaseException:
                pending.close()
                pending_path.unlink(missing_ok=True)
                raise
        try:
            pending_path.replace(out)
        finally:
            pending_path.unlink(missing_ok=True)
    print(out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=ROOT / "outputs/Pordee.apk")
    parser.add_argument("--api", default="35")
    parser.add_argument("--build-tools", default="35.0.0")
    args = parser.parse_args()
    build(args.out.resolve(), args.api, args.build_tools)
