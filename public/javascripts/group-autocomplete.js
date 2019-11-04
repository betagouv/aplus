
var Group = {
    areas: [],
    addArea: function (element) {
        Group.areas.push(element);
        Group.displayArea(element);
    },
    remove: function(code) {
        for (var i = 0; i < Group.areas.length; i++) {
            if (Group.areas[i].code === code) {
                Group.areas.splice(i, 1); 
            }
        }
        while (Group.container.firstChild) {
            Group.container.removeChild(Group.container.firstChild);
        }
        Group.display();
    },
    display: function () {
        Group.areas.forEach(function (e) {
            Group.displayArea(e);
        });
    },
    displayArea: function (element) {
        var label = document.createElement('label');
        label.textContent = element.name;
        var closeButton = document.createElement('input');
        closeButton.setAttribute("type", "button");
        closeButton.name=element.code
        closeButton.value = "X";
        closeButton.addEventListener("click", function (event) {
            Group.remove(element.code);
        });
        var input = document.createElement('input');
        input.setAttribute("class", "mdl-textfield__input area");
        input.setAttribute("type", "hidden");
        input.setAttribute("name", "insee-code[]");
        input.setAttribute("id", "insee-code-label-"+element.code);
        input.setAttribute("value", element.code);
        input.setAttribute("readonly", "");
        var li = document.createElement('li');
        li.appendChild(label);
        li.appendChild(closeButton);
        li.appendChild(input);
        Group.container.appendChild(li);
        Group.input.value = '';
    },
    init: function(data) {
        var iterable = JSON.parse(data);
        if (iterable) {
            iterable.forEach(function (e) {
                Group.addArea(e);
            });
        }
    },
    container: null,
    input: null
};

window.document.addEventListener("DOMContentLoaded", function (event) {
    "use strict";
    Group.container = document.getElementById("group-area-container");
    Group.input = document.getElementById("insee-code");
    Group.init(Group.input.getAttribute("data-lang"))

    // initialize
    var my_autoComplete = new autoComplete({
        selector: 'input[id="insee-code"]',
        minChars: 3,
        source: function(term, response) {
            function reqListener() {
                var results = JSON.parse(this.response);
                var newarray = results.map(myFunction)
                function myFunction(i) {
                    return [ i.name, i.code, i.type ];
                }
                response(newarray);
            }
            var oReq = new XMLHttpRequest();
            oReq.addEventListener("load", reqListener);
            var url = '/territoires/search/?query='+term
            oReq.open("GET", url);
            oReq.send();
        }, renderItem: function (item, search) {
            search = search.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
            var re = new RegExp("(" + search.split(' ').join('|') + ")", "gi");
            return '<div class="autocomplete-suggestion" data-lang="'+item[1]+'" data-langname="'+item[0]+'" data-val="'+search+'">'+(item[0]+ " ("+item[2]+" / "+item[1]+")").replace(re, "<b>$1</b>")+'</div>';
        }, onSelect: function(e, term, item) {
            var element = {
                name: item.getAttribute('data-langname'),
                code: item.getAttribute('data-lang')
            }
            Group.addArea(element);
        }
    });
    // destroy
    //my_autoComplete.destroy();
}, false);
