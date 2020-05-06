// Note: this file is temporary, everything in main.js should be at the bottom of the page
// TODO: The idea is to put everything in main.js in this file, then rename this file as main.js


// Note: `Array.from` does not work in HTMLUnit on NodeList
// so we resort to using this function
function querySelectorAllForEach(selector, exec) {
  var nodes = document.querySelectorAll(selector);
  if (nodes) {
    for (var i = 0; i < nodes.length; i++) {
      exec(nodes[i]);
    }
  }
}

function findAncestor (el, check) {
  while ((el = el.parentElement) && !check(el));
  return el;
}




//
// Header ribbon (demo)
//

function setupDemoBanner() {
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
}

setupDemoBanner();


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
  newNode.innerHTML = '<div class="single--display-flex single--align-items-center">'+
    '<div class="single--margin-right-24px single--font-size-14px">'+infoName+' (facultatif)</div> \
     <div class="mdl-textfield mdl-js-textfield mdl-textfield--no-label single--margin-right-24px"> \
         <input class="mdl-textfield__input mdl-color--white" type="text" id="sample1" name="infos['+infoName+']" value="'+infoValue+'"> \
         <label class="mdl-textfield__label info__label" for="sample1">Saisir '+infoName+' de l’usager ici</label> \
     </div> \
     <div class="'+applicationFormUserInfosRemoveButtonClass+' single--display-flex single--align-items-center"> \
         <button class="mdl-button mdl-js-button mdl-button--icon mdl-button--colored" type="button"> \
             <i class="material-icons">remove_circle</i> \
         </button> \
         <span class="single--cursor-pointer single--text-decoration-underline single--margin-left-2px">Retirer</span> \
     </div> \
  </div>';

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

  querySelectorAllForEach(
    "." + applicationFormUserInfosRemoveButtonClass,
    function (element) {
      // Avoid having many times the same event handler with .onclick
      element.onclick = function(event) {
        removeInfo(element);
      };
    }
  );
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




// <button>
var applicationFormCategoryFilterClass = "aplus-application-form-category-filter-button";
var applicationFormRemoveCategoryFilterClass = "aplus-application-form-remove-category-filter-button";

function applyCategoryFilters() {
  // Get all activated categories organisations
  var selectedCategories = [];
  var parsedOrganisations = []; // local var
  var categoryButtons = document.querySelectorAll(".mdl-chip." + applicationFormCategoryFilterClass);
  for (var i = 0; i < categoryButtons.length; i++) {
    if (categoryButtons[i].classList.contains("mdl-chip--active")) {
      if (categoryButtons[i].dataset.organisations) {
        parsedOrganisations = JSON.parse(categoryButtons[i].dataset.organisations);
        for (var j = 0; j < parsedOrganisations.length; j++) {
          if (selectedCategories.indexOf(parsedOrganisations[j]) === -1) {
            selectedCategories.push(parsedOrganisations[j]);
          }
        }
      }
    }
  }
  console.log("Selected organisations: ", selectedCategories);

  // Show / Hide Checkboxes
  var organisationCheckboxes = document.querySelectorAll(
    "." + applicationFormGroupCheckboxClass);
  var shouldBeFilteredOut = true;
  var thead;
  for (var i = 0; i < organisationCheckboxes.length; i++) {
    if (selectedCategories.length === 0) {
      shouldBeFilteredOut = false;
    }
    // .exists()
    for (var j = 0; j < selectedCategories.length; j++) {
      if (organisationCheckboxes[i].value.toLowerCase()
          .indexOf(selectedCategories[j].toLowerCase()) !== -1) {
        shouldBeFilteredOut = false;
      }
    }

    thead = findAncestor(organisationCheckboxes[i], function(el) {
      return el.nodeName === "THEAD";
    });
    if (shouldBeFilteredOut) {
      thead.classList.add("invisible");
    } else {
      thead.classList.remove("invisible");
    }
    shouldBeFilteredOut = true;
  }
}

function onClickFilterButton(button) {
  // Activate or deactivate button
  if (button.classList.contains("mdl-chip--active")) {
    button.classList.remove("mdl-chip--active");
  } else {
    button.classList.add("mdl-chip--active");
  }

  applyCategoryFilters()
}

function onClickRemoveFilter(button) {
  var selectedCategories = document.querySelectorAll(
    ".mdl-chip.aplus-application-form-remove-category-filter-button");
  var categoryButtons = document.querySelectorAll(".mdl-chip." + applicationFormCategoryFilterClass);
  for (var i = 0; i < categoryButtons.length; i++) {
    categoryButtons[i].classList.remove("mdl-chip--active");
  }

  applyCategoryFilters()
}

function mandatFieldsAreFilled() {
  // 1. Checkbox
  var checkbox = document.getElementById("checkbox-mandat");
  if (checkbox) {
    if (!checkbox.checked) {
      return false;
    }
  }

  // 2. Radios
  var radios = document.querySelectorAll("[id^=mandatType]");
  // Note: same as oneRadioIsChecked = radios.exists(r => r.checked)
  var oneRadioIsChecked = false;
  for (var i = 0; i < radios.length; i++) {
    if (radios[i].checked) {
      oneRadioIsChecked = true;
    }
  }
  if (!oneRadioIsChecked) {
    return false;
  }

  // 3. Date input
  var dateInput = document.getElementById("mandatDate");
  if (!dateInput.value) {
    return false;
  }

  return true;
}

function onMandatFieldChange() {
  if (mandatFieldsAreFilled()) {
    document.querySelector("#review-validation").disabled = false;
  } else {
    document.querySelector("#review-validation").disabled = true;
  }
}



function setupApplicationForm() {
  querySelectorAllForEach(
    "." + applicationFormGroupCheckboxClass,
    function (checkbox) {
      checkbox.addEventListener('click', function(event) {
        showHideUsers(event.target);
      });
    }
  );

  // Mandat
  var checkboxMandat = document.getElementById("checkbox-mandat");
  var radiosMandat = document.querySelectorAll("[id^=mandatType]");
  var dateMandat = document.getElementById("mandatDate");
  if (checkboxMandat) {
    checkboxMandat.addEventListener('change', onMandatFieldChange);
  }
  for (var i = 0; i < radiosMandat.length; i++) {
    radiosMandat[i].addEventListener('change', onMandatFieldChange);
  }
  if (dateMandat) {
    dateMandat.addEventListener('change', onMandatFieldChange);
    dateMandat.addEventListener('keyup', onMandatFieldChange);
    dateMandat.addEventListener('paste', onMandatFieldChange);
    dateMandat.addEventListener('input', onMandatFieldChange);
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

  querySelectorAllForEach(
    "." + applicationFormUserInfosInputClass,
    function (element) {
      element.addEventListener('keydown', function(event) {
        if (event.keyCode === 13) {
          addInfo();
          return false;
        }
      });
    }
  );

  querySelectorAllForEach(
    "." + applicationFormUserInfosAddButtonClass,
    function (element) {
      element.addEventListener('click', function(event) {
        addInfo();
      });
    }
  );

  querySelectorAllForEach(
    "." + applicationFormCategoryFilterClass,
    function (element) {
      element.addEventListener('click', function(event) {
        onClickFilterButton(element);
      });
    }
  );

  querySelectorAllForEach(
    "." + applicationFormRemoveCategoryFilterClass,
    function (element) {
      element.addEventListener('click', function(event) {
        onClickRemoveFilter(element);
      });
    }
  );
}

setupApplicationForm();

var dialogDeleteGroupId = "dialog-delete-group";
var dialogDeleteGroupButtonShowId = "dialog-delete-group-show";
var dialogDeleteGroupButtonCancelId = "dialog-delete-group-cancel";
var dialogDeleteGroupButtonConfirmId = "dialog-delete-group-confirm";
function setupDialog() {
  var dialog = document.getElementById(dialogDeleteGroupId);

  if (dialog) {
    if (!dialog.showModal) {
      dialogPolyfill.registerDialog(dialog);
    }

    querySelectorAllForEach(
        "#" + dialogDeleteGroupButtonCancelId,
        function (element) {
          element.addEventListener('click', function(event) {
            dialog.close();
          });
        }
    );

    querySelectorAllForEach(
        "#" + dialogDeleteGroupButtonShowId,
        function (element) {
          element.addEventListener('click', function(event) {
            dialog.showModal();
          });
        }
    );

    querySelectorAllForEach(
        "#" + dialogDeleteGroupButtonConfirmId,
        function (element) {
          element.addEventListener('click', function(event) {
            var uuid = element.dataset.uuid;
            var url = jsRoutes.controllers.GroupController.deleteUnusedGroupById(uuid).url;
            window.location = url;
          });
        }
    );
  }
}


setupDialog();

//
// Transform <select> with SlimSelect
//

var slimSelectClass = "use-slimselect";

Array.from(document.querySelectorAll("." + slimSelectClass)).forEach(function (select) {
  new SlimSelect({ select: select, selectByGroup: true, closeOnSelect: false});
});



//
// Welcome Page
//

var welcomePageNewletterCheckboxTagId = "aplus-welcome-page-newsletter-checkbox"
var welcomePageNewletterSubmitTagId = "aplus-welcome-page-newsletter-submit"
var welcomePageNewletterCheckbox = document.querySelector("#" + welcomePageNewletterCheckboxTagId);
if (welcomePageNewletterCheckbox != null) {
  welcomePageNewletterCheckbox.addEventListener('click', function() {
    if (welcomePageNewletterCheckbox.checked) {
      document.querySelector("#" + welcomePageNewletterSubmitTagId).disabled = false;
    } else {
      document.querySelector("#" + welcomePageNewletterSubmitTagId).disabled = true;
    }
  });
}
