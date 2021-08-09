## Indic Keyboard

![icon-image](https://gitlab.com/indicproject/indic-keyboard/-/raw/master/graphic-assets/ic_launcher-playstore.png)

Indic Keyboard is a versatile keyboard for Android users who wish to use Indic and Indian languages to type messages, compose emails and generally prefer to use them in addition to English on their phone. You can use this application to type anywhere in your phone that you would normally type in English. It currently supports 23 languages and 54 layouts.

## Download

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=org.smc.inputmethod.indic)
[<img src="https://f-droid.org/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/org.smc.inputmethod.indic/)

## Will my phone support it ?

Indic keyboard supports Android version 4.1 and above (Jellybean, Kitkat and Lollipop). If you can see your language in its native script below you should be able to install and use it. 

Some phones may not support all the languages listed since the phone's maker shipped fonts only for some of these languages. Even then, rendering for some of the languages is not perfect on Android

## What languages (and layouts) are supported ?

- Assamese Keyboard (অসমীয়া) - Inscript, Transliteration
- Arabic Keyboard (العَرَبِيةُ‎‎)
- Bengali Keyboard (বাংলা)- (Probhat, Avro, Inscript, Compact
- Burmese Keyboard (ဗမာ) xkb
- English
- Gujarati Keyboard (ગુજરાતી) - Phonetic, Inscript, Transliteration
- Hindi Keyboard (हिन्दी)- Inscript, Transliteration
- Kannada Keyboard (ಕನ್ನಡ) - Phonetic, Inscript, Transliteration (Baraha), Compact, Anysoft)
- Kashmiri Keyboard (کأشُر) - Inscript, Transliteration
- Malayalam Keyboard (മലയാളം) - Phonetic, Inscript, Transliteration (Mozhi), Swanalekha, Mobile Inscript
- Manipuri Keyboard / Methei Keyboard (মৈতৈলোন্) - Inscript
- Maithili Keyboard (मैथिली)  - Inscript
- Marathi Keyboard (मराठी) - Transliteration
- Mon Keyboard (ဘာသာ မန်;)
- Nepali Keyboard (नेपाली)  Phonetic, Traditional, Transliteration, Inscript
- Oriya Keyboard (ଓଡ଼ିଆ) - Inscript, Transliteration, Lekhani
- Punjabi Keyboard (ਪੰਜਾਬੀ) Phonetic, Inscript, Transliteration
- Sanskrit Keyboard (संस्कृत) Transliteration
- Santali Keyboard (Devanagari script)-(संताली) Inscript
- Sinhala Keyboard / Sinhalese (සිංහල) Transliteration
- Tamil Keyboard (தமிழ்) - Tamil-99, Inscript, Phonetic, Compact, Transliteration
- Telugu Keyboard (తెలుగు) - Phonetic, Inscript, Transliteration, KaChaTaThaPa, Compact
- Urdu Keyboard (اردو) - Navees, Transliteration

## License 

Apache License, Version 2.0

## How do I enable it ?

Indic keyboard has a wizard that will walk you through the process of setting it up so that you can use it comfortably.

## I was using the older version, what happened to my settings ?

Since the code base was updated, you'd have to configure your preferred language again. 
- Click on Settings -> Language & Input 
- Tap on "Set up input methods".
- Tap on the gear icon next to Indic Keyboard.
- Tap on "Languages", uncheck "User system language" and choose your language and layout from the list. 


## Why is there a warning about "collecting data"?

This warning message is a part of the Android operating system. It will appears whenever you try to enable a third party keyboard. We don't collect any data.

## What is a keyboard layout ?

Indic keyboard provides multiple "keyboard layouts". This means that you will have different  ways to type in your native language.

**Transliteration** allows you to type out words using English characters, but will automatically transform the words to your native language. For example, if you type "namaste" in English while using Devanagari transliteration keyboard, it will transform it to नमस्ते correctly.

**Inscript layout** is the standardized keyboard that Government of India came up with to cater for the majority of the languages in India. We support the full specification, and if you are already familiar with Inscript on your Desktop, it will work on the phone too.

**Phonetic keyboard** is similar to Transliteration scheme - you can type what the words sound like using English characters and it will be automatically transformed into your language.

**Compact Keyboard** allows to type Indian languages without the shift key. You can long press on the letters to get more options.

Other layouts are specific to the languages - do try them out.

## HOWTO Build
1. Install gradle, Android Support Repository, SDK and other usual android stuffs, 
    1. Download the necessary tools from https://developer.android.com/studio/index.html and install them. Also set the necessary environment variables like `ANDROID_HOME`
    1. You can optionally use the development environment provided as a Docker image which has all the necessary tools bundled in it
        1. Pull the image using the command `docker pull registry.gitlab.com/smc/indic-keyboard:dev`
        2. Run bash on it using `docker run -it registry.gitlab.com/balasankarc/indic-keyboard:dev bash`
1. `git clone --recursive git@gitlab.com:indicproject/indic-keyboard.git`
1. build jni lib `cd native/jni && ndk-build -e "APP_ABI=armeabi-v7a" -C ./`
1. `cd java`
1. `gradle assembleDebug` to build the package.

## Supporters

Government of India's Department of IT R & D project undertaken by ICFOSS funded the implementation of Mobile Compact Layout and Lollipop AOSP support. Another small aid from <a href=http://icfoss.in>ICFOSS</a> via Kerala Govt Sources enabled  addition of Swanalekha Layout (beta) and maintenance and updates on existing Malayalam Layouts.

Kannada Anysoft keyboard layout was funded via [Bounty Source](https://www.bountysource.com/issues/3406116-anysoft-like-kannada-inscript-keyboard-15) by Thejesh GN

## Contributions and Credits

Jishnu Mohan is the founder and maintainer.

Anoop P Contributed Swanalekha Layout(beta) and converted [java-morelangs  module](https://github.com/androidtweak/java-morelangs) to an [Android library](https://gitlab.com/smc/android-ime).

Sridhar Contributed Kannada Anysoft Keyboard layout.

Hiran Venugopalan contributed Graphic Assets for the project and helped with re-branding 2.0 version.

Anivar Aravind helped with project management.

Indic Project community members (Akshay, Balasankar, Ashik, Santhosh ) contributed documentation.

Many keyboard layouts were adapted from the [jquery.ime](https://github.com/wikimedia/jquery.ime) project of Wikimedia Foundation. They were contributed by volunteers.

Akshy contributed in layout bug fixes and in tooling.

Some icons from from FontAwesome, they are in [Creative Commons Attribution 4.0 International license](https://fontawesome.com/license)

Also thank [LineageOS](https://review.lineageos.org/c/LineageOS/android_packages_inputmethods_LatinIME/), [Openboard](https://github.com/dslul/openboard/), and [Simple-Keyboard](https://github.com/rkkr/simple-keyboard) some changes are taken/adopted from these keyboards

## TODO
