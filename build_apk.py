# -*- coding: utf-8 -*-
"""
Compila el APK de Tropical Fish sin Gradle, usando las build-tools del
Android SDK local (aapt2 + javac + d8 + zipalign + apksigner).

Uso:   python build_apk.py
Salida: build/tropical_fish.apk (firmado con keystore de depuración)
"""
import glob, os, subprocess, sys, zipfile, shutil

RAIZ = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(RAIZ, 'build')
GEN = os.path.join(BUILD, 'gen')
CLASSES = os.path.join(BUILD, 'classes')
DEX = os.path.join(BUILD, 'dex')

SDK = os.path.join(os.environ.get('LOCALAPPDATA', ''), 'Android', 'Sdk')
BT = os.path.join(SDK, 'build-tools', '36.0.0')
ANDROID_JAR = os.path.join(SDK, 'platforms', 'android-37.0', 'android.jar')

AAPT2 = os.path.join(BT, 'aapt2.exe')
D8 = os.path.join(BT, 'd8.bat')
ZIPALIGN = os.path.join(BT, 'zipalign.exe')
APKSIGNER = os.path.join(BT, 'apksigner.bat')
def herramienta_jdk(nombre):
    """Busca javac/keytool en el PATH, JAVA_HOME o C:\\Program Files\\Java\\jdk-*"""
    encontrado = shutil.which(nombre)
    if encontrado:
        return encontrado
    home = os.environ.get('JAVA_HOME')
    candidatos = ([os.path.join(home, 'bin', nombre + '.exe')] if home else []) + \
        glob.glob(os.path.join(r'C:\Program Files\Java', 'jdk-*', 'bin', nombre + '.exe'))
    for c in candidatos:
        if os.path.exists(c):
            return c
    sys.exit(f'No se encontro {nombre}; instala un JDK o define JAVA_HOME')


KEYTOOL = herramienta_jdk('keytool')
JAVAC = herramienta_jdk('javac')
KS = os.path.join(BUILD, 'debug.keystore')


def run(cmd, **kw):
    print('>', ' '.join(os.path.basename(str(c)) for c in cmd[:2]), '...', flush=True)
    r = subprocess.run([str(c) for c in cmd], capture_output=True, text=True, **kw)
    if r.returncode != 0:
        print(r.stdout, r.stderr)
        sys.exit(f'FALLO: {cmd[0]}')
    return r


def main():
    os.makedirs(BUILD, exist_ok=True)
    for d in (GEN, CLASSES, DEX):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)

    # 1. Compilar recursos (aapt2)
    res_zip = os.path.join(BUILD, 'res.zip')
    run([AAPT2, 'compile', '--dir', os.path.join(RAIZ, 'res'), '-o', res_zip])

    # 2. Enlazar recursos + manifest + R.java
    apk_sin_firmar = os.path.join(BUILD, 'app-unsigned.apk')
    run([AAPT2, 'link', '-o', apk_sin_firmar,
         '-I', ANDROID_JAR,
         '--manifest', os.path.join(RAIZ, 'AndroidManifest.xml'),
         '--java', GEN, res_zip])

    # 3. Compilar Java (MainActivity + R generado)
    fuentes = []
    for base, _, files in os.walk(os.path.join(RAIZ, 'src')):
        fuentes += [os.path.join(base, f) for f in files if f.endswith('.java')]
    for base, _, files in os.walk(GEN):
        fuentes += [os.path.join(base, f) for f in files if f.endswith('.java')]
    # --release 17: d8 no acepta class files de JDKs mas nuevos (p.ej. v69 = Java 25)
    run([JAVAC, '-encoding', 'utf-8', '--release', '17',
         '-cp', ANDROID_JAR, '-d', CLASSES] + fuentes)

    # 4. DEX
    clases = []
    for base, _, files in os.walk(CLASSES):
        clases += [os.path.join(base, f) for f in files if f.endswith('.class')]
    run([D8, '--lib', ANDROID_JAR, '--output', DEX] + clases)

    # 5. Meter classes.dex dentro del APK (sin comprimir, como exige Android)
    apk_con_dex = os.path.join(BUILD, 'app-dex.apk')
    shutil.copy(apk_sin_firmar, apk_con_dex)
    with zipfile.ZipFile(apk_con_dex, 'a') as z:
        z.write(os.path.join(DEX, 'classes.dex'), 'classes.dex',
                compress_type=zipfile.ZIP_STORED)

    # 6. Keystore de depuración (Android estándar: android/androiddebugkey)
    if not os.path.exists(KS):
        run([KEYTOOL, '-genkeypair', '-keystore', KS, '-alias', 'androiddebugkey',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
             '-storepass', 'android', '-keypass', 'android',
             '-dname', 'CN=Android Debug,O=Android,C=US'])

    # 7. Alinear y firmar
    apk_final = os.path.join(BUILD, 'tropical_fish.apk')
    run([ZIPALIGN, '-f', '4', apk_con_dex, os.path.join(BUILD, 'app-aligned.apk')])
    run([APKSIGNER, 'sign', '--ks', KS, '--ks-pass', 'pass:android',
         '--key-pass', 'pass:android',
         '--out', apk_final, os.path.join(BUILD, 'app-aligned.apk')])

    print(f'\nAPK listo: {apk_final}')


if __name__ == '__main__':
    main()
