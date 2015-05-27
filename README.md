## Indic Keyboard
AOSP keyboard ported to support more languges.

![icon-image](https://cloud.githubusercontent.com/assets/321335/7845586/792e62a8-04d5-11e5-84bb-2425c04c5253.png)

Indic keyboard is a free and open source Indian language input keyboard application for Android mobile devices. At present supporting about 16+ Indic languages, this application has multiple input methods for each language - direct input (characters mapped to keys), inscript and transliteration. Though support for Indian languages was already available in some phones, Android officially started support with Jelly bean (4.1) version. Device need to haave font and rendering support for the keyboard to work.

This keyboard requires Android 4.1+.

## Requirements

* Android 4.1 and above.

## Supported Languages & Layouts
* Assamese: Inscript, Transliteration
* Arabic
* Bengali: Probhat, Avro, Inscript, Compact
* Burmese/Myanmar: xkb
* English
* Gujarati: Phonetic, Inscript, Transliteration
* Hindi: Phonetic, Inscript, Transliteration
* Kannada:  Phonetic, Inscript, Transliteration, Compact, Anysoft
* Kashmiri/Kashur: Inscript, Transliteration
* Maithili: Inscript
* Malayalam: Phonetic, Inscript, Mozhi, Swanalekha
* Marathi: Transliteration
* Manipuri: Inscript
* Mon
* Nepali: Phonetic, Traditional, Transliteration, Inscript
* Oriya/Odia: Inscript, Transliteration, Lekhani
* Punjabi: Phonetic, Inscript, Transliteration
* Sanskrit: Transliteration
* Santali: Inscript
* Sinhala: Transliteration
* Tamil: Tamil-99, Inscript, Phonetic, Compact, Transliteration
* Telugu: Phonetic, Inscript, Transliteration, KaChaTaThaPa, Compact
* Urdu: Transliteration

## License 

Apache License, Version 2.0

## HOWTO Build
1. Install gradle, Android Support Repository, SDK and other usual android stuffs.
2. `git clone --recursive git@github.com:smc/Indic-Keyboard.git`
4. `cd java`
5. Use `gradle assembleDebug` to build the package.

## Credits
This project is originally initiated by Jishnu Mohan in 2012 as part of his `androidtweak.in` project.

1.0 version was supported by DIT R & D Project undertaken by ICFOSS, Govt. of Kerala. Anivar Aravind co-ordinated the project for ICFOSS and Hiran Venugopalan designed the icon.

This project uses code from jquery.ime project of Wikimedia and some of the layouts are ported from there.

## TODO

