if [ $# -lt 1 ]
then
    echo "Usage: $0 language"
fi

url="http://dumps.wikimedia.org/${1}wiki/latest/${1}wiki-latest-pages-articles.xml.bz2"
wget $url
