CsvImport = {
};

window.document.addEventListener("DOMContentLoaded", function(event) {
    CsvImport.instructorAllLink = document.getElementById("instructor-all-link");
    CsvImport.instructorNoneLink = document.getElementById("instructor-none-link");

    CsvImport.instructorAllLink.addEventListener("click", function(event) {
        // Check every checkbox
        CsvImport.checkboxes = window.document.getElementsByClassName("instructor");
        for (var i = 0; i < CsvImport.checkboxes.length; i++) {
            CsvImport.checkboxes[i].checked = true;
        }
    });

    CsvImport.instructorNoneLink.addEventListener("click", function(event) {
        // Uncheck every checkbox
        CsvImport.checkboxes = window.document.getElementsByClassName("instructor");
        for (var i = 0; i < CsvImport.checkboxes.length; i++) {
            CsvImport.checkboxes[i].checked = false;
        }
    });
}, false);