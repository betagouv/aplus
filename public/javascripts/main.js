var extract = 9;
function onSearch() {
    var searchTerm = document.getElementById("search-input").value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "");
    if(searchTerm.length > 2) {
        Array.from(document.querySelectorAll("tfoot")).forEach(function (row) { row.classList.remove("invisible") });
        Array.from(document.querySelectorAll(".searchable-row")).forEach(function (row) {
            if (searchTerm.length > 2) {
                var searchData = row.getAttribute("data-search");
                var searchResult = searchData.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "").indexOf(searchTerm);
                if(searchResult !== -1) {
                    row.classList.remove("invisible");
                    var minIndex = Math.max(searchResult - extract, 0);
                    var maxIndex = Math.min(searchResult + searchTerm.length + extract, searchData.length);
                    row.querySelector(".search-cell").innerHTML = searchData.substring(minIndex, searchResult) +
                        '<span style="font-weight: bold; background-color: #FFFF00;">'+searchData.substring(searchResult, searchResult + searchTerm.length)+
                        "</span>"+searchData.substring(searchResult + searchTerm.length, maxIndex);
                } else {
                    row.classList.add("invisible");
                    row.querySelector(".search-cell").innerHTML = "";
                }
            }
        });
    } else {
        Array.from(document.querySelectorAll(".searchable-row")).forEach(function (row) { row.classList.remove("invisible"); });
        Array.from(document.querySelectorAll(".search-cell")).forEach(function (cell) { cell.innerHTML=""; });
        Array.from(document.querySelectorAll("tfoot")).forEach(function (row) { row.classList.add("invisible") });
    }
}

function clearSearch() {
    document.getElementById("search-input").value = "";
    onSearch();
}

function changeMDLInputChecked(input, isChecked) {
    input.checked = isChecked;
    if(isChecked) {
        input.parentNode.classList.add("is-checked");
    } else {
        input.parentNode.classList.remove("is-checked");
    }
    componentHandler.upgradeElements(input);
}

function addValidationForButton(objectSelector, buttonSelector) {
    var object = document.querySelector(objectSelector);
    object.addEventListener('click', function() {
        document.querySelector(buttonSelector).disabled = false;
    });
}