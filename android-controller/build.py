"""Build and verify the native viewer home against the exact upstream Android release.
Requires Python >=3.11, JDK 17+, Android platform jar/build-tools and Google bundletool."""

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parent
OFFICIAL = "2983ae01209954d51bf55a915a43d334ec41898082acc6cc01e439df2c5f8c07"
BUNDLETOOL = "a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
SIGNATURES = {"META-INF/CERT.SF", "META-INF/CERT.RSA", "META-INF/MANIFEST.MF"}


def digest(file):
    with open(file, "rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def run(*cmd):
    subprocess.run([str(x) for x in cmd], check=True)


def build(args, folder):
    if digest(args.official) != OFFICIAL or digest(args.bundletool) != BUNDLETOOL:
        raise ValueError("Unexpected upstream APK or bundletool hash")
    for name in ("classes", "metadata", "dex"):
        (folder / name).mkdir()

    def native(name):
        return args.tools / (name + (".exe" if os.name == "nt" else ""))

    def java(tool):
        path = shutil.which(tool)
        if not path:
            raise RuntimeError("Missing JDK tool " + tool)
        return path

    run(
        java("javac"),
        "--release",
        "8",
        "-encoding",
        "UTF-8",
        "-cp",
        args.android_jar,
        "-d",
        folder / "classes",
        ROOT / "HomeActivity.java",
    )
    run(java("jar"), "cf", folder / "ui.jar", "-C", folder / "classes", ".")
    run(
        java("java"),
        "-cp",
        args.tools / "lib/d8.jar",
        "com.android.tools.r8.D8",
        "--release",
        "--min-api",
        "21",
        "--lib",
        args.android_jar,
        "--output",
        folder / "dex",
        folder / "ui.jar",
    )
    run(
        java("javac"),
        "-encoding",
        "UTF-8",
        "-cp",
        args.bundletool,
        "-d",
        folder / "metadata",
        ROOT / "Manifest.java",
    )
    run(
        native("aapt2"),
        "convert",
        "--output-format",
        "proto",
        "-o",
        folder / "proto.apk",
        args.official,
    )
    with ZipFile(folder / "proto.apk") as proto:
        (folder / "manifest.pb").write_bytes(proto.read("AndroidManifest.xml"))
        run(
            java("java"),
            "-cp",
            os.pathsep.join(map(str, (folder / "metadata", args.bundletool))),
            "Manifest",
            folder / "manifest.pb",
            folder / "updated.pb",
        )
        with ZipFile(folder / "manifest-build.apk", "w") as updated:
            for item in proto.infolist():
                if item.filename == "AndroidManifest.xml":
                    updated.writestr(item, (folder / "updated.pb").read_bytes())
                elif item.filename == "resources.pb" or item.filename.startswith(
                    "res/"
                ):
                    updated.writestr(item, proto.read(item))
    run(
        native("aapt2"),
        "convert",
        "--output-format",
        "binary",
        "-o",
        folder / "converted.apk",
        folder / "manifest-build.apk",
    )
    with ZipFile(args.official) as base, ZipFile(
        folder / "converted.apk"
    ) as converted, ZipFile(folder / "unsigned.apk", "w") as out:
        if "classes2.dex" in base.namelist():
            raise ValueError("Unexpected upstream DEX layout")
        for item in base.infolist():
            if item.filename in SIGNATURES:
                continue
            data = (
                converted.read(item.filename)
                if item.filename == "AndroidManifest.xml"
                else base.read(item)
            )
            item.extra = b""  # zipalign determines alignment for this archive
            out.writestr(item, data)
        out.write(folder / "dex/classes.dex", "classes2.dex", ZIP_DEFLATED)
        for name in (
            "LICENSE",
            "README.md",
            "HomeActivity.java",
            "Manifest.java",
            "build.py",
        ):
            out.write(ROOT / name, "assets/desklink-source/" + name, ZIP_DEFLATED)
    run(
        native("zipalign"),
        "-P",
        "16",
        "-f",
        "4",
        folder / "unsigned.apk",
        folder / "aligned.apk",
    )
    signer = ["java", "-jar", args.tools / "lib/apksigner.jar"]
    run(
        *signer,
        "sign",
        "--ks",
        args.keystore,
        "--ks-key-alias",
        args.alias,
        "--ks-pass",
        "env:DESKLINK_SIGNING_PASSWORD",
        "--key-pass",
        "env:DESKLINK_SIGNING_PASSWORD",
        "--out",
        args.out,
        folder / "aligned.apk"
    )
    run(*signer, "verify", "--verbose", args.out)
    run(native("zipalign"), "-c", "-P", "16", "4", args.out)
    with ZipFile(args.official) as base, ZipFile(args.out) as out:
        for item in base.infolist():
            if (
                item.filename != "AndroidManifest.xml"
                and item.filename not in SIGNATURES
            ):
                if out.read(item.filename) != base.read(item):
                    raise ValueError("Modified upstream component: " + item.filename)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    for name in ("official", "bundletool", "tools", "android-jar", "keystore", "out"):
        p.add_argument("--" + name, type=lambda x: Path(x).resolve(), required=True)
    p.add_argument("--alias", required=True)
    args = p.parse_args()
    args.out.parent.mkdir(exist_ok=True, parents=True)
    with tempfile.TemporaryDirectory(dir=args.out.parent) as d:
        build(args, Path(d))
