// Author: Jishnu Mohan <jishnu7@gmail.com>

var file = process.argv[2];
if(!file) {
  return console.log("Error: Pass file name as parameter");
}


var toHex = function(char) {
  var hex = char.charCodeAt(0).toString(16).toUpperCase();
  if(char.length < 4) {
    hex = "0" + hex;
  }
  return "&#x" + hex + ";";
};

jQuery = {
  ime: {
    register: function(data) {
      data.patterns.forEach(function(key) {
        var char = key[1],
          pos = key[0];
        console.log("<!---", pos, "    ", char, "--->");
        console.log('            <Key\n' +
                    '                latin:keyLabel="' + toHex(char) +'"\n' +
                    '                latin:keyLabelFlags="fontNormal|followKeyLargeLabelRatio" />\n');
      });
    }
  }
};

var a = require("./" + file);
