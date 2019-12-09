ApplicationAttachment = {};

window.document.addEventListener("DOMContentLoaded", function (event) {
    "use strict";
    ApplicationAttachment.list = document.getElementById("attachment-list");
    ApplicationAttachment.fileInputCount = 1;
    ApplicationAttachment.idSequence = 0; // In order to avoid id collisions
    ApplicationAttachment.increaseOrDecreaseFileInputList = function (event) {
        if (event.target.files.length > 0) {
            // If a new file is added, add a new file input.
            var li = document.createElement("li");
            var input = document.createElement("input");
            input.setAttribute("type", "file");
            input.setAttribute("name", "file["+(ApplicationAttachment.idSequence++)+"]");
            ApplicationAttachment.fileInputCount++;
            input.addEventListener("change", ApplicationAttachment.increaseOrDecreaseFileInputList);
            ApplicationAttachment.list.appendChild(li);
            li.appendChild(input);
        } else {
            // If a previous file is unset for upload, remove the corresponding input file.
            if (ApplicationAttachment.fileInputCount > 1) {
                event.target.parentElement.parentElement.removeChild(event.target.parentElement);
                ApplicationAttachment.fileInputCount--;
            }
        }
    };
    // For all file inputs, add a listener that adds a new file input to allow
    // for more attachments to be made.
    ApplicationAttachment.list.querySelectorAll("input").forEach(function (input) {
        input.addEventListener("change", ApplicationAttachment.increaseOrDecreaseFileInputList);
    });
}, false);
