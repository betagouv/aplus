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


function deleteElement(selector) {
  var element = document.querySelector(selector);
  element.parentNode.removeChild(element);
}

function checkAllBySelector(selector) {
  var checkboxes = window.document.querySelectorAll(selector);
  for (var i = 0; i < checkboxes.length; i++) {
    checkboxes[i].checked = true;
    checkboxes[i].parentElement.classList.add("is-checked");
  }
}

function uncheckAllBySelector(selector) {
  var checkboxes = window.document.querySelectorAll(selector);
  for (var i = 0; i < checkboxes.length; i++) {
    checkboxes[i].checked = false;
    checkboxes[i].parentElement.classList.remove("is-checked");
  }
}


var Main = {
  disableEventTarget: function(element) {
    element.disabled = 'true';
  }
}

//
// Form Functions
//

var aplusFormHasBeenSubmitted = false;

// https://developer.mozilla.org/en-US/docs/Web/API/WindowEventHandlers/onbeforeunload
function setupOnbeforeunload(event) {
  var message = "Merci de confirmer la fermeture de cette page, les données saisies ne seront pas enregistrées.";
  // Cancel the event (HTML Specification)
  event.preventDefault();
  // Chrome requires returnValue to be set
  event.returnValue = message;
  return message;
}

function removeOnbeforeunload(event) {
  window.onbeforeunload = null;
}

function setupOneProtectedForm(form) {
  if (form == null) {
    return;
  }
  console.log("Protection of the form will be activated.");

  var elem;
  var changeableElems = form.querySelectorAll("input, textarea, select");
  for (var i = 0; i < changeableElems.length; i++) {
    elem = changeableElems[i];
    elem.addEventListener("input", function () {
      if (!aplusFormHasBeenSubmitted) {
        // do not use `window.addEventListener("beforeunload", setupOnbeforeunload)`
        // it won't work...
        window.onbeforeunload = setupOnbeforeunload;
      } else {
        window.onbeforeunload = null;
      }
    });
  }

  form.addEventListener("submit", function(event) {
    aplusFormHasBeenSubmitted = true;
    removeOnbeforeunload(event);
  });
}

// Uses the class "aplus-protected-form"
// Note: It is not possible to stop the popup appearing in the function of onbeforeunload
// setting window.onbeforeunload = null, will just stop the popup the *next* time.
function setupProtectedForms() {
  var forms = document.querySelectorAll(".aplus-protected-form");
  for (var fi = 0; fi < forms.length; fi++) {
    setupOneProtectedForm(forms[fi]);
  }
}


//
// Notification Messages
//

// Will add onClick listeners on `.notification__close-btn` that remove the `.notification` element
function setupNotificationMessages() {
  var elems = document.querySelectorAll(".notification");
  for (var i = 0; i < elems.length; i++) {
    var closeBtn = elems[i].querySelector(".notification__close-btn");
    if (closeBtn != null) {
      onClickRemoveElement(closeBtn, elems[i])
    }
  }
}

function onClickRemoveElement(clickElement, elementToRemove) {
  clickElement.addEventListener("click", function() {
    // Cross browser
    // https://stackoverflow.com/questions/3387427/remove-element-by-id
    elementToRemove.outerHTML = ""
  })
}

/*
window.document.addEventListener("DOMContentLoaded", function(event) {
  //setupProtectedForms();
  setupNotificationMessages();
}, false);
*/
if(/localhost|demo/.test(window.location.hostname)) {
  var ribon = document.getElementById("header__ribbon");
  if(ribon) {
    ribon.classList.add("invisible");
  }
  var elements = document.getElementsByClassName("demo-only");
  for (var i = 0; i < elements.length; i++) {
    elements[i].classList.remove("invisible");
  }
}
