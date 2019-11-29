ApplicationAttachment = {};

window.document.addEventListener("DOMContentLoaded", function (event) {
    "use strict";
    ApplicationAttachment.list = document.getElementById("attachment-list");
    ApplicationAttachment.currentCount = 1;
    ApplicationAttachment.maxId = 0;
    ApplicationAttachment.inputChangedEventListener = function (event) {
        if(event.target.files.length > 0) {
            // Add one line.
            var li = document.createElement("li");
            var input = document.createElement("input");
            input.setAttribute("type", "file");
            input.setAttribute("name", "file["+(ApplicationAttachment.maxId++)+"]");
            ApplicationAttachment.currentCount++;
            input.addEventListener("change", ApplicationAttachment.inputChangedEventListener);
            ApplicationAttachment.list.appendChild(li);
            li.appendChild(input);
        } else {
            // Remove this line if it's not the ultimate.
            if(ApplicationAttachment.currentCount > 1) {
                event.target.parentElement.parentElement.removeChild(event.target.parentElement);
                ApplicationAttachment.currentCount--;
            }
        }
    };
    var length = ApplicationAttachment.list.childNodes.length;
    ApplicationAttachment.list.querySelectorAll("input").forEach(function (input) {
        input.addEventListener("change", ApplicationAttachment.inputChangedEventListener);
    });
}, false);
