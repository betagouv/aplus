import { findAncestor } from "./helpers";

const createApplicationFormId = 'create-application-form';
const invitedGroupsCheckboxClass = 'application-form-invited-groups-checkbox';
const usagerInfosTypesSelectId = 'aplus-application-form-user-infos-types-select-id';
const usagerInfosAddButtonClass = 'aplus-application-form-user-infos-add-button';
const usagerInfosRemoveButtonClass = 'aplus-application-form-user-infos-remove-button';
const usagerInfosInputClass = 'aplus-application-form-user-infos-input';
const categoryFilterClass = 'aplus-application-form-category-filter-button';
const removeCategoryFilterClass = 'aplus-application-form-remove-category-filter-button';

const inputPrenomId = "usagerPrenom";
const inputNomId = "usagerNom";
const inputBirthDateId = "usagerBirthDate";
const mandatTypeSmsRadioId = "mandatType_sms";

const mandatSmsPhoneInputName = "mandat-sms-phone";
const mandatSmsSendButtonId = "mandat-sms-send-button";
const mandatSmsSuccessId = "mandat-sms-success";
const mandatSmsValidationFailedId = "mandat-sms-validation-failed";
const mandatSmsErrorServerId = "mandat-sms-error-server";
const mandatSmsErrorBrowserId = "mandat-sms-error-browser";
const linkedMandatInputId = "linkedMandat";



interface SmsMandatFormData {
  prenom: string;
  nom: string;
  birthDate: string;
  phoneNumber: string;
}

// models.mandat.Mandat
interface Mandat {
  id: string;
}


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
    .querySelectorAll<HTMLElement>('.' + usagerInfosRemoveButtonClass)
    .forEach((element) => {
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
  // Get all activated categories organisations
  let selectedCategories: Array<string> = [];
  document.querySelectorAll<HTMLElement>(".mdl-chip." + categoryFilterClass)
    .forEach((categoryButton) => {
      if (categoryButton.classList.contains("mdl-chip--active")) {
        if (categoryButton.dataset['organisations']) {
          const parsedOrganisations: Array<string> =
            JSON.parse(categoryButton.dataset['organisations']);
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
  document.querySelectorAll<HTMLInputElement>('.' + invitedGroupsCheckboxClass)
    .forEach((invitedGroupCheckbox) => {
      const groupName: string = invitedGroupCheckbox.dataset['groupName'];
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
  document.querySelectorAll<HTMLElement>(".mdl-chip." + categoryFilterClass)
    .forEach((categoryButton) => categoryButton.classList.remove("mdl-chip--active"));
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
      element.addEventListener('keydown', (event) => {
        if ((<KeyboardEvent>event).keyCode === 13) {
          addInfo();
        }
      });
    });

  document
    .querySelectorAll("." + usagerInfosAddButtonClass)
    .forEach((element) => {
      element.addEventListener('click', () => addInfo());
    });

  document
    .querySelectorAll<HTMLElement>("." + categoryFilterClass)
    .forEach((element) => {
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
          const groupName = input.dataset['groupName'];
          groupName && addInvitedGroupInfos(groupName);
        } else {
          infosDiv && infosDiv.classList.add('invisible');
        }
      })
    );
}



//
// SMS Mandat Card
//

function setupMandatSmsForm() {
  const inputPrenom = <HTMLInputElement>document.getElementById(inputPrenomId);
  const inputNom = <HTMLInputElement>document.getElementById(inputNomId);
  const inputBirthDate = <HTMLInputElement>document.getElementById(inputBirthDateId);
  const inputPhoneNumber = <HTMLInputElement>document.getElementById(mandatSmsPhoneInputName);

  const sendButton = <HTMLButtonElement | null>document.getElementById(mandatSmsSendButtonId);
  const successMessage = document.getElementById(mandatSmsSuccessId);
  const validationFailedMessage = document.getElementById(mandatSmsValidationFailedId);
  const serverErrorMessage = document.getElementById(mandatSmsErrorServerId);
  const browserErrorMessage = document.getElementById(mandatSmsErrorBrowserId);
  const linkedMandatInput = <HTMLInputElement>document.getElementById(linkedMandatInputId);
  const mandatTypeSmsRadio = document.getElementById(mandatTypeSmsRadioId);


  // Returns null|string
  function validateNonEmptyInput(input: HTMLInputElement) {
    const data = input.value;
    const parent = <HTMLElement>input.parentNode;
    if (data) {
      parent.classList.remove("is-invalid");
      return data;
    } else {
      parent.classList.add("is-invalid");
      return null;
    }
  }

  function validatePhoneNumber(input: HTMLInputElement) {
    const data = input.value.replace(/\s/g, '');
    const parent = <HTMLElement>inputPhoneNumber.parentNode;
    if (/^\d{10}$/.test(data)) {
      parent.classList.remove("is-invalid");
      return data;
    } else {
      parent.classList.add("is-invalid");
      return null;
    }
  }

  function validateForm() {
    const prenom = validateNonEmptyInput(inputPrenom);
    const nom = validateNonEmptyInput(inputNom);
    const birthDate = validateNonEmptyInput(inputBirthDate);
    const phoneNumber = validatePhoneNumber(inputPhoneNumber);
    const isValid = prenom && nom && birthDate && phoneNumber;

    return {
      isValid: isValid,
      data: {
        prenom: prenom,
        nom: nom,
        birthDate: birthDate,
        phoneNumber: phoneNumber
      }
    };
  }

  function sendForm(
    data: SmsMandatFormData,
    callbackSuccess: (mandat: Mandat) => void,
    callbackServerError: () => void,
    callbackBrowserError: () => void
  ) {
    try {
      const xhr = new XMLHttpRequest();
      xhr.onreadystatechange = function() {
        if (xhr.readyState == XMLHttpRequest.DONE) {   // XMLHttpRequest.DONE == 4
          try {
            if (Math.floor(xhr.status / 100) === 2) {
              callbackSuccess(<Mandat>JSON.parse(xhr.responseText));
            } else {
              callbackServerError();
            }
          } catch (error) {
            console.error(error);
            callbackBrowserError();
          }
        }
      };
      xhr.open("POST", "/mandats/sms", true);
      xhr.setRequestHeader("Content-Type", "application/json");
      // Play recommends putting the CSRF token, even for AJAX request
      // and cites browser plugins as culprits
      // https://www.playframework.com/documentation/2.8.x/ScalaCsrf#Plays-CSRF-protection
      const tokenInput = <HTMLInputElement>document.querySelector("input[name=csrfToken]");
      const token = tokenInput.value;
      xhr.setRequestHeader("Csrf-Token", token);
      xhr.send(JSON.stringify(data));
    } catch (error) {
      console.error(error);
      callbackBrowserError();
    }
  }

  if (sendButton) {
    sendButton.onclick = function(event) {
      event.preventDefault();
      successMessage.classList.add("hidden");
      validationFailedMessage.classList.add("hidden");
      serverErrorMessage.classList.add("hidden");
      browserErrorMessage.classList.add("hidden");
      const formData = validateForm();
      if (formData.isValid) {
        sendButton.disabled = true;
        sendForm(
          formData.data,
          // Success
          function(mandat: Mandat) {
            const link = successMessage.querySelector("a");
            link.href = "/mandats/" + mandat.id;
            linkedMandatInput.value = mandat.id;
            successMessage.classList.remove("hidden");
            // Note: mandatTypeSmsRadio.checked = true does not show the radio as checked
            mandatTypeSmsRadio.click();
          },
          // Server error (= logged by Sentry)
          function() {
            // Wait 30s
            setTimeout(function() { sendButton.disabled = false; }, 30000);
            serverErrorMessage.classList.remove("hidden");
          },
          // Browser error (= not logged by Sentry)
          function() {
            // Wait 30s
            setTimeout(function() { sendButton.disabled = false; }, 30000);
            browserErrorMessage.classList.remove("hidden");
          }
        );
      } else {
        validationFailedMessage.classList.remove("hidden");
      }
    }
  }
}




setupDynamicUsagerInfosButtons();
setupInvitedGroups();
setupMandatSmsForm();
