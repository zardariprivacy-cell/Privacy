set -e
export JAVA_HOME=~/workspace/tools/jdk
BT=~/workspace/tools/android-sdk/build-tools/34.0.0
AJAR=~/workspace/tools/android-sdk/platforms/android-34/android.jar
PROJ=~/workspace/apps/guftagu-android
export PATH=$JAVA_HOME/bin:$BT:$PATH
cd $PROJ
rm -rf build
mkdir -p build/compiled build/gen build/classes build/dex build/stage

echo "== aapt2 compile =="
aapt2 compile --dir app/src/main/res -o build/compiled/res.zip

echo "== aapt2 link =="
aapt2 link -o build/stage/app.apk -I $AJAR --manifest app/src/main/AndroidManifest.xml \
  --java build/gen \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 4 --version-name 2.2 \
  build/compiled/res.zip

echo "== javac =="
find app/src/main/java build/gen -name "*.java" > build/sources.txt
javac -encoding UTF-8 --release 8 -cp "$AJAR" -d build/classes @build/sources.txt

echo "== jar + d8 =="
(cd build/classes && jar cf ../classes.jar .)
d8 --min-api 26 --lib $AJAR --output build/dex build/classes.jar
ls -la build/dex/

echo "== package dex =="
cp build/dex/classes*.dex build/stage/
(cd build/stage && zip -q app.apk classes*.dex && rm classes*.dex)

echo "== zipalign + sign =="
zipalign -f -p 4 build/stage/app.apk build/app-aligned.apk
KS=~/workspace/apps/debug.keystore
apksigner sign --ks $KS --ks-pass pass:android --key-pass pass:android --out build/guftagu.apk build/app-aligned.apk
apksigner verify build/guftagu.apk && echo "signature OK"
ls -la build/guftagu.apk
aapt2 dump badging build/guftagu.apk | head -6
echo "== GUFTAGU BUILD DONE =="
