// Author: Jishnu Mohan <jishnu7@gmail.com>

var http = require('http'),
  async = require('async');

var file = process.argv[2];
if(!file) {
  return console.log("Error: Pass file name as parameter");
}

var sort = [
  'Q','W','E','R','T','Y','U','I','O','P','\\{','\\}','|',
  'q','w','e','r','t','y','u','i','o','p','\\[','\\]','\\',
  'A','S','D','F','G','H','J','K','L',':', '\"',null,null,
  'a','s','d','f','g','h','j','k','l',';', '\'',null,null,
  'Z','X','C','V','B','N','M','<','>','?',null,null,null,
  'z','x','c','v','b','n','m',',','.','/',null,null,null,
  '`','1','2','3','4','5','6','7','8','9','0','-','=',
  '~','!','@','#','$','%','^','&','\\*','\\(','\\)','_','\\+'
];

var toHex = function(char) {
  var hex = char.charCodeAt(0).toString(16).toUpperCase();
  if(char.length < 4) {
    hex = "0" + hex;
  }
  return "&#x" + hex + ";";
};

var charDetails = function(char, callback) {
  var query = {
      method:  "chardetails.getdetails",
      params: [char],
      id: ""
    };
  query = JSON.stringify(query);

  var req = http.request({
    host: "silpa.org.in",
    //host: "localhost",
    path: "/JSONRPC",
    port: '80',
    //port: '5000',
    method: "POST",
    headers: {
      "Accept": "application/json, text/javascript, */*;",
      "Content-Type": "application/json; charset=UTF-8",
      "Content-Length": query.length + 2
    }
  }, function (res) {
    var data = "";
    res.addListener('data', function(chunk) {
      data += chunk;
    });
    res.addListener('end', function() {
      var val=" ";
      try {
        val = JSON.parse(data);
        val = val.result[char].Name;
      } catch (e) {
      }
      callback(val);
    });
  }).end(query);
};

jQuery = {
  ime: {
    register: function(data) {
      var out = [],
        extra = [],
        calls = [];

      data.patterns.forEach(function(key) {
        calls.push(function(callback) {
          var char = key[1],
            pos = key[0],
            index = sort.indexOf(pos);

          charDetails(char, function(name) {
            if(index !== -1) {
              out[sort.indexOf(pos)] = '\n<!---' + pos + ' ' + char + ' ' + name + '--->\n' +
                '<Key\n' +
                'latin:keyLabel="' + toHex(char) +'"\n' +
                'latin:keyLabelFlags="fontNormal|followKeyLargeLabelRatio" />';
            } else {
              extra.push(pos + " " + toHex(char) + " " + char + name + "\n");
            }
            callback();
          });
        });
      });

      async.parallel(calls, function(err, results) {
        out.forEach(function(line) {
          console.log(line.replace(/\n/g, "\n    "));
        });
        if(extra.length > 0) {
          console.log("\n\n\n", extra);
        }
      });
    }
  }
};

var a = require("./" + file);
