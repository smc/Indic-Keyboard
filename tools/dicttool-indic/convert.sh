#!/bin/bash
for filename in ../*.combined; do
  IFS=_ read lang <<< `basename $filename`
  echo "$lang"
  java -jar dicttool_aosp.jar makedict -s $filename -d main_${lang}.dict
done
