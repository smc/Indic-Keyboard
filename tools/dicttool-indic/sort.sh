out=(${1//wiki-/ })

bzcat $1 | grep -v '<[a-z]*\s' | grep -v '&[a-z0-9]*;' | tr '[:punct:][:blank:][:digit:]' '\n' | tr 'A-Z' 'a-z' | grep -v '[a-z]' | uniq | sort -f | uniq -c | sort -nr | head -50000 | tail -n +2 | awk '{print " word="$2",f="$1}' > ${out}_wordlist.combined

# unix timestamp
date +%s
