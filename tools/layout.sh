# Author: Jishnu Mohan <jishnu7@gmail.com>
# 04-03-2014
if [ $# -lt 1 ]
then
    echo "Usage : $0 language type"
    exit
fi
case "$2" in
    inscript)
        files=('kbd_kannada_inscript.xml' 'keyboard_layout_set_kannada_inscript.xml'
            'rows_kannada_inscript.xml'
            'rowkeys_kannada_inscript1.xml' 'rowkeys_kannada_inscript2.xml'
            'rowkeys_kannada_inscript3.xml' 'rowkeys_kannada_inscript4.xml')
        ;;
    *)
        files=('kbd_kannada.xml' 'keyboard_layout_set_kannada.xml'
            'rows_kannada.xml' 'rowkeys_kannada1.xml'
            'rowkeys_kannada2.xml' 'rowkeys_kannada3.xml')
        ;;
esac


root=`git rev-parse --show-toplevel`
cd $root/java/res/xml/
for i in "${files[@]}"
do
    echo "copying... $i"
    sed "s/kannada/${1}/g" $i > ${i/kannada/$1}
done
