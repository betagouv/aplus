const createApplicationFormId = 'create-application-form';
const invitedGroupsCheckboxClass = 'application-form-invited-groups-checkbox';
const applicationFormUserInfosTypesSelectId = 'aplus-application-form-user-infos-types-select-id';
const applicationFormUserInfosAddButtonClass = 'aplus-application-form-user-infos-add-button';
const applicationFormUserInfosRemoveButtonClass = 'aplus-application-form-user-infos-remove-button';
const applicationFormUserInfosInputClass = 'aplus-application-form-user-infos-input';
const applicationFormCategoryFilterClass = 'aplus-application-form-category-filter-button';
const applicationFormRemoveCategoryFilterClass = 'aplus-application-form-remove-category-filter-button';





function addInvitedGroupInfos(groupName: string) {
  function checkDoesNotExist(name: string) {
    var existingInfos: NodeListOf<HTMLInputElement> =
      document.querySelectorAll("input[name^='usagerOptionalInfos[']");
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


function newTypeSelected() {
  var select = <HTMLSelectElement | null>document
    .getElementById(applicationFormUserInfosTypesSelectId);
  var otherTypeElement = <HTMLInputElement | null>document.getElementById("other-type");
  if (select.value === "Autre") {
    otherTypeElement.value = "";
    (<HTMLElement>otherTypeElement.parentNode).classList.remove("invisible");
    //componentHandler.upgradeElements(otherTypeElem.parentNode.parentNode.getElementsByTagName("*"));
  } else {
    (<HTMLElement>otherTypeElement.parentNode).classList.add("invisible");
    otherTypeElement.value = select.value;
  }
  if (select.value !== "") {
    (<HTMLElement>document.getElementById("other-type-value").parentNode)
      .classList.remove("invisible");
    document.getElementById("add-infos__ok-button").classList.remove("invisible");
  }
  componentHandler.upgradeDom();
}

function addOptionalInfoRow(infoName: string, infoValue: string) {
  var newNode = document.createElement("div");
  newNode.innerHTML = '<div class="single--display-flex single--align-items-center">' +
    '<div class="single--margin-right-24px single--font-size-14px">' + infoName + ' (facultatif)</div> \
     <div class="mdl-textfield mdl-js-textfield mdl-textfield--no-label single--margin-right-24px"> \
         <input class="mdl-textfield__input mdl-color--white" type="text" id="sample1" name="usagerOptionalInfos['+ infoName + ']" value="' + infoValue + '"> \
         <label class="mdl-textfield__label info__label" for="sample1">Saisir '+ infoName + ' de l’usager ici</label> \
     </div> \
     <div class="'+ applicationFormUserInfosRemoveButtonClass + ' single--display-flex single--align-items-center"> \
         <button class="mdl-button mdl-js-button mdl-button--icon mdl-button--colored" type="button"> \
             <i class="material-icons">remove_circle</i> \
         </button> \
         <span class="single--cursor-pointer single--text-decoration-underline single--margin-left-2px">Retirer</span> \
     </div> \
  </div>';

  var otherDiv = document.getElementById("other-div");
  otherDiv.parentNode.insertBefore(newNode, otherDiv);
  componentHandler.upgradeElements(newNode);
  var select = <HTMLSelectElement | null>document
    .getElementById(applicationFormUserInfosTypesSelectId);
  if (infoName !== "Autre") {
    select && Array.from(select.options).forEach((option) => {
      if (option.value === infoName) {
        select.removeChild(option);
      }
    });
  }
  select.value = "";
  document.getElementById("add-infos__ok-button").classList.add("invisible");

  document
    .querySelectorAll('.' + applicationFormUserInfosRemoveButtonClass)
    .forEach((element: HTMLElement) => {
      // Avoid having many times the same event handler with .onclick
      element.onclick = () => {
        var row = element.parentNode;
        row.parentNode.removeChild(row);
      };
    });
}

function addInfo() {
  const inputInfoName = <HTMLInputElement | null>document.getElementById("other-type");
  const infoName = inputInfoName.value;
  inputInfoName.value = "";
  const inputInfoNameParent = <HTMLElement>inputInfoName.parentNode;
  inputInfoNameParent.classList.remove("is-dirty");
  inputInfoNameParent.classList.add("invisible");
  var valueInput = <HTMLInputElement | null>document.getElementById("other-type-value");
  var infoValue = valueInput.value;
  valueInput.value = "";
  const valueInputParent = <HTMLElement>valueInput.parentNode;
  valueInputParent.classList.remove("is-dirty");
  valueInputParent.classList.add("invisible");
  if (infoName === "" || infoValue === "") { return; }
  addOptionalInfoRow(infoName, infoValue);
}


function findAncestor(el, check) {
  while ((el = el.parentElement) && !check(el));
  return el;
}

function applyCategoryFilters() {
  // Get all activated categories organisations
  var selectedCategories = [];
  var parsedOrganisations = []; // local var
  var categoryButtons = document.querySelectorAll(".mdl-chip." + applicationFormCategoryFilterClass);
  for (var i = 0; i < categoryButtons.length; i++) {
    if (categoryButtons[i].classList.contains("mdl-chip--active")) {
      if ((<HTMLElement>categoryButtons[i]).dataset.organisations) {
        parsedOrganisations = JSON.parse((<HTMLElement>categoryButtons[i]).dataset.organisations);
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
  var organisationCheckboxes: NodeListOf<HTMLInputElement> = document
    .querySelectorAll('.' + invitedGroupsCheckboxClass);
  var shouldBeFilteredOut = true;
  var thead;
  for (var i = 0; i < organisationCheckboxes.length; i++) {
    const groupCheckbox = organisationCheckboxes[i];
    // TODO: add the org name
    const organisationName = groupCheckbox.dataset.groupName;
    if (selectedCategories.length === 0) {
      shouldBeFilteredOut = false;
    }
    // .exists()
    for (var j = 0; j < selectedCategories.length; j++) {
      if (organisationName.toLowerCase()
        .indexOf(selectedCategories[j].toLowerCase()) !== -1) {
        shouldBeFilteredOut = false;
      }
    }

    thead = findAncestor(groupCheckbox, function(el) {
      return el.nodeName === "DIV";
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


function setupDynamicUsagerInfosButtons() {

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

  document
    .querySelectorAll("." + applicationFormUserInfosInputClass)
    .forEach((element) => {
      element.addEventListener('keydown', function(event: KeyboardEvent) {
        if (event.keyCode === 13) {
          addInfo();
          return false;
        }
      });
    });

  document
    .querySelectorAll("." + applicationFormUserInfosAddButtonClass)
    .forEach((element) => {
      element.addEventListener('click', function(event) {
        addInfo();
      });
    });

  document
    .querySelectorAll("." + applicationFormCategoryFilterClass)
    .forEach((element) => {
      element.addEventListener('click', function(event) {
        onClickFilterButton(element);
      });
    });

  document
    .querySelectorAll("." + applicationFormRemoveCategoryFilterClass)
    .forEach((element) => {
      element.addEventListener('click', function(event) {
        onClickRemoveFilter(element);
      });
    });

}



function setupInvitedGroups() {
  document
    .querySelectorAll('.' + invitedGroupsCheckboxClass)
    .forEach((element) =>
      element.addEventListener('click', (event) => {
        const input = <HTMLInputElement>event.target;
        const groupId = input.value;
        const infosDiv = document.getElementById(`invite-${ groupId }-additional-infos`);
        if (input.checked) {
          infosDiv && infosDiv.classList.remove('invisible');
          const groupName = input.dataset.groupName;
          groupName && addInvitedGroupInfos(groupName);
        } else {
          infosDiv && infosDiv.classList.add('invisible');
        }
      })
    );
}



setupDynamicUsagerInfosButtons();
setupInvitedGroups();
