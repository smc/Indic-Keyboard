cd src
javac com/android/inputmethod/keyboard/tools/*.java
jar cmf ../etc/manifest.txt makekeyboardtext.jar ./ ../res/
java -jar makekeyboardtext.jar -java out
mv out/res/src/com/android/inputmethod/keyboard/internal/KeyboardTextsTable.java ../../../java/src/com/android/inputmethod/keyboard/internal/KeyboardTextsTable.java
rm -rf out
rm makekeyboardtext.jar
