const createApplicationFormId = 'create-application-form';
const invitedGroupsCheckboxClass = 'application-form-invited-groups-checkbox';
const usagerInfosTypesSelectId = 'aplus-application-form-user-infos-types-select-id';
const usagerInfosAddButtonClass = 'aplus-application-form-user-infos-add-button';
const usagerInfosRemoveButtonClass = 'aplus-application-form-user-infos-remove-button';
const usagerInfosInputClass = 'aplus-application-form-user-infos-input';
const categoryFilterClass = 'aplus-application-form-category-filter-button';
const removeCategoryFilterClass = 'aplus-application-form-remove-category-filter-button';



function addInvitedGroupInfos(groupName: string) {
  function checkDoesNotExist(name: string) {
    const existingInfos: NodeListOf<HTMLInputElement> =
      document.querySelectorAll("input[name^='usagerOptionalInfos[']");
    const alreadyHasInput = Array.from(existingInfos)
      .some((infoInput) => infoInput.name.includes(name));
    return !alreadyHasInput;
  }

  // CAF / CNAF => Identifiant CAF
  // CPAM / MSA / CNAV => Numéro de sécurité sociale
  if (/CN?AF(\s|$)/i.test(groupName)) {
    const name = "Identifiant CAF";
    if (checkDoesNotExist(name)) {
      addOptionalInfoRow(name, "");
    }
  } else if (/(CPAM|MSA|CNAV)(\s|$)/i.test(groupName)) {
    const name = "Numéro de sécurité sociale";
    if (checkDoesNotExist(name)) {
      addOptionalInfoRow(name, "");
    }
  }

}



function newTypeSelected() {
  const select = <HTMLSelectElement | null>document
    .getElementById(usagerInfosTypesSelectId);
  const otherTypeElement = <HTMLInputElement | null>document.getElementById("other-type");
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
  const newNode = document.createElement("div");
  newNode.innerHTML = '<div class="single--display-flex single--align-items-center">' +
    '<div class="single--margin-right-24px single--font-size-14px">' + infoName + ' (facultatif)</div> \
     <div class="mdl-textfield mdl-js-textfield mdl-textfield--no-label single--margin-right-24px"> \
         <input class="mdl-textfield__input mdl-color--white" type="text" id="sample1" name="usagerOptionalInfos['+ infoName + ']" value="' + infoValue + '"> \
         <label class="mdl-textfield__label info__label" for="sample1">Saisir '+ infoName + ' de l’usager ici</label> \
     </div> \
     <div class="'+ usagerInfosRemoveButtonClass + ' single--display-flex single--align-items-center"> \
         <button class="mdl-button mdl-js-button mdl-button--icon mdl-button--colored" type="button"> \
             <i class="material-icons">remove_circle</i> \
         </button> \
         <span class="single--cursor-pointer single--text-decoration-underline single--margin-left-2px">Retirer</span> \
     </div> \
  </div>';

  const otherDiv = document.getElementById("other-div");
  otherDiv.parentNode.insertBefore(newNode, otherDiv);
  componentHandler.upgradeElements(newNode);
  const select = <HTMLSelectElement | null>document
    .getElementById(usagerInfosTypesSelectId);
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
    .querySelectorAll('.' + usagerInfosRemoveButtonClass)
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



function applyCategoryFilters() {
  function findAncestor(el: HTMLElement, check: (e: HTMLElement) => boolean): HTMLElement {
    while ((el = el.parentElement) && !check(el));
    return el;
  }

  // Get all activated categories organisations
  let selectedCategories: Array<string> = [];
  document.querySelectorAll(".mdl-chip." + categoryFilterClass)
    .forEach((categoryButton: HTMLElement) => {
      if (categoryButton.classList.contains("mdl-chip--active")) {
        if (categoryButton.dataset.organisations) {
          const parsedOrganisations: Array<string> =
            JSON.parse(categoryButton.dataset.organisations);
          parsedOrganisations.forEach((parsedOrganisation) => {
            if (!selectedCategories.includes(parsedOrganisation)) {
              selectedCategories.push(parsedOrganisation);
            }
          });
        }
      }
    });
  console.log("Selected organisations: ", selectedCategories);

  // Show / Hide Checkboxes
  document.querySelectorAll('.' + invitedGroupsCheckboxClass)
    .forEach((invitedGroupCheckbox: HTMLInputElement) => {
      const groupName: string = invitedGroupCheckbox.dataset.groupName;
      const isSelected = selectedCategories.some((selectedCategory) =>
        groupName.toLowerCase().includes(selectedCategory.toLowerCase()))
      let shouldBeFilteredOut = !isSelected;
      if (selectedCategories.length === 0) {
        shouldBeFilteredOut = false;
      }

      const thead = findAncestor(invitedGroupCheckbox, (el) => el.nodeName === "DIV");
      if (shouldBeFilteredOut) {
        thead.classList.add("invisible");
      } else {
        thead.classList.remove("invisible");
      }
    });
}

function onClickFilterButton(button: HTMLElement) {
  // Activate or deactivate button
  if (button.classList.contains("mdl-chip--active")) {
    button.classList.remove("mdl-chip--active");
  } else {
    button.classList.add("mdl-chip--active");
  }

  applyCategoryFilters()
}

function onClickRemoveFilter() {
  document.querySelectorAll(".mdl-chip." + categoryFilterClass)
    .forEach((categoryButton: HTMLElement) => categoryButton.classList.remove("mdl-chip--active"));
  applyCategoryFilters()
}


function setupDynamicUsagerInfosButtons() {

  var userInfosTypesSelect = document.getElementById(usagerInfosTypesSelectId);
  if (userInfosTypesSelect) {
    newTypeSelected();
    userInfosTypesSelect.addEventListener('change', () => newTypeSelected());
  }

  var createApplicationForm = document.getElementById(createApplicationFormId);
  if (createApplicationForm) {
    createApplicationForm.addEventListener('submit', () => addInfo());
  }

  document
    .querySelectorAll("." + usagerInfosInputClass)
    .forEach((element) => {
      element.addEventListener('keydown', (event: KeyboardEvent) => {
        if (event.keyCode === 13) {
          addInfo();
          return false;
        }
      });
    });

  document
    .querySelectorAll("." + usagerInfosAddButtonClass)
    .forEach((element) => {
      element.addEventListener('click', () => addInfo());
    });

  document
    .querySelectorAll("." + categoryFilterClass)
    .forEach((element: HTMLElement) => {
      element.addEventListener('click', () => onClickFilterButton(element));
    });

  document
    .querySelectorAll("." + removeCategoryFilterClass)
    .forEach((element) => {
      element.addEventListener('click', () => onClickRemoveFilter());
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
