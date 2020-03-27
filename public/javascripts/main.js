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


//
// Application form
//

var createApplicationFormId = "create-application-form";

// <input type="checkbox">
var applicationFormGroupCheckboxClass = "aplus-application-form-group";

function applicationFormAddSelectedGroupInfos(groupName) {
  function checkDoesNotExist(name) {
    var existingInfos = document.querySelectorAll("input[name^='infos[']");
    for (var i = 0; i < existingInfos.length; i++) {
      if (existingInfos[i].name.indexOf(name) !== -1) {
        return false;
      }
    }
    return true;
  }

  // CAF / CNAF => Identifiant CAF
  // CPAM / MSA / CNAV => Numéro de sécurité sociale
  if (/CN?AF(\s|$)/i.test(groupName)) {
    var name = "Identifiant CAF";
    if (checkDoesNotExist(name)) {
      addOptionalInfoRow(name, "");
    }
  } else if (/(CPAM|MSA|CNAV)(\s|$)/i.test(groupName)) {
    var name = "Numéro de sécurité sociale";
    if (checkDoesNotExist(name)) {
      addOptionalInfoRow(name, "");
    }
  }
}

function showHideUsers(sender) {
  var thead = sender.parentNode.parentNode.parentNode.parentNode;
  var tbody = thead.nextElementSibling;
  var inputs = tbody.querySelectorAll("[type=checkbox]");
  if(sender.checked) {
    for(var i = 0; i < inputs.length; i++){
      var input = inputs[i];
      input.checked = true;
      input.parentNode.classList.add("is-checked");
      componentHandler.upgradeElements(input);
    }
    tbody.classList.remove("invisible");
    applicationFormAddSelectedGroupInfos(sender.value);
  } else {
    for(var i = 0; i < inputs.length; i++){
      var input = inputs[i];
      input.checked = false;
      input.parentNode.classList.remove("is-checked");
      componentHandler.upgradeElements(input);
    }
    tbody.classList.add("invisible");
  }

}


// <select> of info type
var applicationFormUserInfosTypesSelectId = "aplus-application-form-user-infos-types-select-id";
// <button>
var applicationFormUserInfosAddButtonClass = "aplus-application-form-user-infos-add-button";
var applicationFormUserInfosRemoveButtonClass = "aplus-application-form-user-infos-remove-button";
// <input>
var applicationFormUserInfosInputClass = "aplus-application-form-user-infos-input";


function newTypeSelected() {
  var select = document.getElementById(applicationFormUserInfosTypesSelectId);
  var otherTypeElement = document.getElementById("other-type");
  if(select.value === "Autre") {
    otherTypeElement.value = "";
    otherTypeElement.parentNode.classList.remove("invisible");
    //componentHandler.upgradeElements(otherTypeElem.parentNode.parentNode.getElementsByTagName("*"));
  } else {
    otherTypeElement.parentNode.classList.add("invisible");
    otherTypeElement.value = select.value;
  }
  if(select.value !== "") {
    document.getElementById("other-type-value").parentNode.classList.remove("invisible");
    document.getElementById("add-infos__ok-button").classList.remove("invisible");
  }
  componentHandler.upgradeDom();
}

function removeInfo(elem) {
  var row = elem.parentNode;
  row.parentNode.removeChild(row);
}

function addOptionalInfoRow(infoName, infoValue) {
  var newNode = document.createElement("div");
  newNode.innerHTML = ''+infoName+' (facultatif)'+'<br> \
                       <div class="mdl-textfield mdl-js-textfield --margin-right-20px"> \
                           <input class="mdl-textfield__input mdl-color--white" type="text" id="sample1" name="infos['+infoName+']" value="'+infoValue+'"> \
                            <label class="mdl-textfield__label info__label" for="sample1">Saisir '+infoName+' de l\'usager ici</label> \
                       </div> \
                    <button class="mdl-button mdl-js-button mdl-button--icon mdl-button--colored '+applicationFormUserInfosRemoveButtonClass+'" type="button"> \
                        <i class="material-icons">remove_circle</i> \
                    </button>';

  var otherDiv = document.getElementById("other-div");
  otherDiv.parentNode.insertBefore(newNode, otherDiv);
  componentHandler.upgradeElements(newNode);
  var select = document.getElementById(applicationFormUserInfosTypesSelectId);
  if (infoName !== "Autre") {
    for (var i = 0; i < select.options.length; i++) {
      if (select.options[i].value === infoName) {
        select.removeChild(select.options[i]);
      }
    }
  }
  select.value = "";
  document.getElementById("add-infos__ok-button").classList.add("invisible");

  Array
    .from(document.querySelectorAll("." + applicationFormUserInfosRemoveButtonClass))
    .forEach(function (element) {
      // Avoid having many times the same event handler with .onclick
      element.onclick = function(event) {
        removeInfo(element);
      };
    });
}

function addInfo() {
  var inputInfoName = document.getElementById("other-type");
  var infoName = inputInfoName.value;
  inputInfoName.value = "";
  inputInfoName.parentNode.classList.remove("is-dirty");
  inputInfoName.parentNode.classList.add("invisible");
  var valueInput = document.getElementById("other-type-value");
  var infoValue = valueInput.value;
  valueInput.value = "";
  valueInput.parentNode.classList.remove("is-dirty");
  valueInput.parentNode.classList.add("invisible");
  if (infoName === "" || infoValue === "") { return; }
  addOptionalInfoRow(infoName, infoValue);
}



function setupApplicationForm() {
  Array
    .from(document.querySelectorAll("." + applicationFormGroupCheckboxClass))
    .forEach(function (checkbox) {
      checkbox.addEventListener('click', function(event) {
        showHideUsers(event.target);
      });
    });

  var checkboxMandat = document.getElementById("checkbox-mandat");

  if (checkboxMandat) {
    checkboxMandat.addEventListener('click', function() {
      if(checkboxMandat.checked) {
        document.querySelector("#review-validation").disabled = false;
      } else {
        document.querySelector("#review-validation").disabled = true;
      }
    });
  }

  var userInfosTypesSelect = document.getElementById(applicationFormUserInfosTypesSelectId);
  if (userInfosTypesSelect) {
    newTypeSelected();
    userInfosTypesSelect.addEventListener('change', function() {
      newTypeSelected();
    });
  }

  var createApplicationForm = document.getElementById(createApplicationFormId);
  if (createApplicationForm) {
    createApplicationForm.addEventListener('submit', function() {
      addInfo();
    });
  }

  Array
    .from(document.querySelectorAll("." + applicationFormUserInfosInputClass))
    .forEach(function (element) {
      element.addEventListener('keydown', function(event) {
        if (event.keyCode === 13) {
          addInfo();
          return false;
        }
      });
    });

  Array
    .from(document.querySelectorAll("." + applicationFormUserInfosAddButtonClass))
    .forEach(function (element) {
      element.addEventListener('click', function(event) {
        addInfo();
      });
    });

}


//
// Functions executed by the script
//


window.document.addEventListener("DOMContentLoaded", function(event) {
  setupProtectedForms();
  setupNotificationMessages();
  setupApplicationForm();
}, false);

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
