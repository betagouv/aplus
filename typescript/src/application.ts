/* global jsRoutes */
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
const inputBirthdateId = "usagerBirthDate";



// Maps to the scala class
interface MandatGeneration {
  usagerPrenom: string;
  usagerNom: string;
  usagerBirthdate: string;
  creatorGroupId: string | null;
}

interface MandatFormData {
  prenom: string | null;
  nom: string | null;
  birthdate: string | null;
  creatorGroup: { id: string, name: string } | null;
  isValid: boolean;
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
  if (select != null && otherTypeElement != null) {
    if (select.value === "Autre") {
      otherTypeElement.value = "";
      (<HTMLElement>otherTypeElement.parentNode).classList.remove("invisible");
      //componentHandler.upgradeElements(otherTypeElem.parentNode.parentNode.getElementsByTagName("*"));
    } else {
      (<HTMLElement>otherTypeElement.parentNode).classList.add("invisible");
      otherTypeElement.value = select.value;
    }
    if (select.value !== "") {
      (<HTMLElement>document.getElementById("other-type-value")?.parentNode)
        .classList.remove("invisible");
      document.getElementById("add-infos__ok-button")?.classList.remove("invisible");
    }
    componentHandler.upgradeDom();
  }
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
  if (otherDiv == null) { return; }
  otherDiv.parentNode?.insertBefore(newNode, otherDiv);
  componentHandler.upgradeElements(newNode);
  const select = <HTMLSelectElement | null>document
    .getElementById(usagerInfosTypesSelectId);
  if (select == null) { return; }
  if (infoName !== "Autre") {
    select && Array.from(select.options).forEach((option) => {
      if (option.value === infoName) {
        select.removeChild(option);
      }
    });
  }
  select.value = "";
  document.getElementById("add-infos__ok-button")?.classList.add("invisible");

  document
    .querySelectorAll<HTMLElement>('.' + usagerInfosRemoveButtonClass)
    .forEach((element) => {
      // Avoid having many times the same event handler with .onclick
      element.onclick = () => {
        const row = element.parentNode;
        if (row == null) { return; }
        row.parentNode?.removeChild(row);
      };
    });
}



function addInfo() {
  let infoName = "";
  let infoValue = "";

  const inputInfoName = <HTMLInputElement | null>document.getElementById("other-type");
  if (inputInfoName != null) {
    infoName = inputInfoName.value;
    inputInfoName.value = "";
    const inputInfoNameParent = <HTMLElement>inputInfoName.parentNode;
    inputInfoNameParent.classList.remove("is-dirty");
    inputInfoNameParent.classList.add("invisible");
  }

  const valueInput = <HTMLInputElement | null>document.getElementById("other-type-value");
  if (valueInput != null) {
    infoValue = valueInput.value;
    valueInput.value = "";
    const valueInputParent = <HTMLElement>valueInput.parentNode;
    valueInputParent.classList.remove("is-dirty");
    valueInputParent.classList.add("invisible");
  }
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
      const groupOrgId: string | undefined = invitedGroupCheckbox.dataset['organisationId'];
      const isSelected = selectedCategories.some((selectedCategory) => {
        if (groupOrgId == null) {
          return false;
        } else {
          return groupOrgId === selectedCategory;
        }
      });
      let shouldBeFilteredOut = !isSelected;
      if (selectedCategories.length === 0) {
        shouldBeFilteredOut = false;
      }

      const thead = findAncestor(invitedGroupCheckbox, (el) => el.nodeName === "DIV");
      if (shouldBeFilteredOut) {
        thead?.classList.add("invisible");
      } else {
        thead?.classList.remove("invisible");
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



function setupMandatForm() {
  const inputPrenom = <HTMLInputElement>document.getElementById(inputPrenomId);
  const inputNom = <HTMLInputElement>document.getElementById(inputNomId);
  const inputBirthdate = <HTMLInputElement>document.getElementById(inputBirthdateId);

  // Single group only
  const inputCreatorGroupId = <HTMLInputElement>document
    .getElementById("aplus-application-form-creator-group-id");
  const inputCreatorGroupName = <HTMLInputElement>document
    .getElementById("aplus-application-form-creator-group-name");

  // Multi-group only
  const selectCreatorGroup = <HTMLSelectElement | null>document
    .getElementById("aplus-application-form-creator-group");

  const successLink = document.getElementById("mandat-generation-link");
  const validationFailedMessage = document.getElementById("mandat-generation-validation-failed");
  const serverErrorMessage = document.getElementById("mandat-generation-error-server");
  const browserErrorMessage = document.getElementById("mandat-generation-error-browser");
  const hasChangedErrorMessage = document.getElementById("mandat-generation-form-has-changed");
  const linkedMandatInput = <HTMLInputElement>document.getElementById("linked-mandat");

  const mandatGenerationOption = <HTMLInputElement>document.getElementById("mandat-option-generate");
  const mandatGenerationBox = <HTMLElement>document.getElementById("mandat-generation-box");

  let ajaxRequestIsRunning: boolean = false;
  let lastMandatGenerationData: { form: MandatGeneration, mandat: Mandat } | null = null;



  function resetMandatMessages() {
    successLink?.classList.add("hidden");
    validationFailedMessage?.classList.add("hidden");
    serverErrorMessage?.classList.add("hidden");
    browserErrorMessage?.classList.add("hidden");
    hasChangedErrorMessage?.classList.add("hidden");
  }

  function mandatFormDataHasNotChanged(formData: MandatFormData): boolean {
    if (lastMandatGenerationData) {
      let sameCreatorGroupId: boolean;
      if (formData.creatorGroup) {
        sameCreatorGroupId = lastMandatGenerationData.form.creatorGroupId === formData.creatorGroup.id;
      } else {
        sameCreatorGroupId = lastMandatGenerationData.form.creatorGroupId == null;
      }
      let hasNotChanged: boolean = false;
      if (formData.prenom && formData.nom && formData.birthdate) {
        hasNotChanged = formData.prenom === lastMandatGenerationData.form.usagerPrenom &&
          formData.nom === lastMandatGenerationData.form.usagerNom &&
          formData.birthdate === lastMandatGenerationData.form.usagerBirthdate &&
          sameCreatorGroupId;
      }
      return hasNotChanged;
    } else {
      return false;
    }
  }

  function mandatPageUrl(mandat: Mandat): string {
    return jsRoutes.controllers.MandatController.mandat(mandat.id).url + "#impression-automatique";
  }

  function readNonEmptyInput(input: HTMLInputElement, showError: boolean): string | null {
    const data = input.value;
    const parent = <HTMLElement>input.parentNode;
    if (data) {
      parent.classList.remove("is-invalid");
      return data;
    } else {
      if (showError) {
        parent.classList.add("is-invalid");
      }
      return null;
    }
  }

  function readCreatorGroup(): { id: string, name: string } | null {
    if (selectCreatorGroup) {
      const selectedOption = selectCreatorGroup.options[selectCreatorGroup.selectedIndex];
      if (selectedOption) {
        return {
          id: selectCreatorGroup.value,
          name: selectedOption.text,
        };
      }
    }
    if (inputCreatorGroupId && inputCreatorGroupName) {
      return {
        id: inputCreatorGroupId.value,
        name: inputCreatorGroupName.value,
      };
    }
    return null;
  }

  function readMandatForm(showError: boolean): MandatFormData {
    const prenom = readNonEmptyInput(inputPrenom, showError);
    const nom = readNonEmptyInput(inputNom, showError);
    const birthdate = readNonEmptyInput(inputBirthdate, showError);
    const creatorGroup = readCreatorGroup();

    const isValid = (prenom != null) && (nom != null) && (birthdate != null);

    return {
      prenom: prenom,
      nom: nom,
      birthdate: birthdate,
      creatorGroup: creatorGroup,
      isValid: isValid,
    };
  }

  function showMandatFieldValues(formData: MandatFormData) {
    const setDataText = (el: HTMLElement | null, data: string | null) => {
      if (el) {
        if (data) {
          el.classList.remove("aplus-color-text--error");
          el.classList.add("single--font-weight-bold");
          el.innerText = data;
        } else {
          el.classList.add("aplus-color-text--error");
          el.classList.remove("single--font-weight-bold");
          el.innerText = "(invalide)";
        }
      }
    }
    const prenomEl = document.getElementById("mandat-form-data-prenom");
    const nomEl = document.getElementById("mandat-form-data-nom");
    const birthdateEl = document.getElementById("mandat-form-data-birthdate");
    const creatorGroupEl = document.getElementById("mandat-form-data-creator-group");

    setDataText(prenomEl, formData.prenom);
    setDataText(nomEl, formData.nom);
    setDataText(birthdateEl, formData.birthdate);
    setDataText(creatorGroupEl, formData.creatorGroup ? formData.creatorGroup.name : null);

    const button = <HTMLElement | null>document.getElementById("mandat-generate-button");
    if (button) {
      if (formData.isValid) {
        button.classList.remove("mdl-button--disabled");
        button.addEventListener("click", mandatGenerationAction);
        const hasChanged = !mandatFormDataHasNotChanged(readMandatForm(false));
        if (hasChanged && lastMandatGenerationData != null) {
          successLink?.classList.add("hidden");
          hasChangedErrorMessage?.classList.remove("hidden");
        }
      } else {
        button.classList.add("mdl-button--disabled");
        button.removeEventListener("click", mandatGenerationAction);
      }
    }
  }

  // Listen to changes in the form in order to update the mandat
  function setupFormListeners() {
    const creationUrl: string = jsRoutes.controllers.ApplicationController.create().url;
    // Avoid being on the wrong page containing the inputs
    if (document.location.href.includes(creationUrl)) {
      [inputPrenom, inputNom, inputBirthdate].forEach((input) => {
        input.addEventListener("change", () => {
          let form = readMandatForm(false);
          showMandatFieldValues(form);
        });
      });
      // Multigroup case
      if (selectCreatorGroup) {
        selectCreatorGroup.addEventListener("change", () => {
          let form = readMandatForm(false);
          showMandatFieldValues(form);
        });
      }
    }
  }

  function setupMandatTypeRadioListeners() {
    const setupMandatBox = () => {
      let form;
      if (mandatGenerationOption.checked) {
        form = readMandatForm(true);
        mandatGenerationBox.classList.remove("hidden");
      } else {
        form = readMandatForm(false);
        mandatGenerationBox.classList.add("hidden");
      }
      showMandatFieldValues(form);
    }
    if (mandatGenerationOption) {

      // Initial state
      setupMandatBox();

      // Listeners
      Array.from(document.querySelectorAll("input[name=mandatGenerationType]"))
        .forEach((element) => {
          // Radios can be triggered by click or keyboard
          element.addEventListener("change", setupMandatBox);
        });
    }
  }

  function mandatGenerationAction(event: Event) {
    event.preventDefault();

    if (ajaxRequestIsRunning) {
      return;
    }

    resetMandatMessages();

    const formData = readMandatForm(true);
    if (formData.isValid && formData.prenom && formData.nom && formData.birthdate) {

      if (mandatFormDataHasNotChanged(formData)) {
        if (lastMandatGenerationData) {
          successLink?.classList.remove("hidden");
          const mandatUrl = mandatPageUrl(lastMandatGenerationData.mandat);
          window.open(mandatUrl, "_blank");
          return;
        }
      }

      const url: string = jsRoutes.controllers.MandatController.generateNewMandat().url;
      const payload: MandatGeneration = {
        usagerPrenom: formData.prenom,
        usagerNom: formData.nom,
        usagerBirthdate: formData.birthdate,
        creatorGroupId: formData.creatorGroup ? formData.creatorGroup.id : null,
      };

      // Play recommends putting the CSRF token, even for AJAX request
      // and cites browser plugins as culprits
      // https://www.playframework.com/documentation/2.8.x/ScalaCsrf#Plays-CSRF-protection
      const tokenInput = <HTMLInputElement>document.querySelector("input[name=csrfToken]");
      const token = tokenInput.value;

      ajaxRequestIsRunning = true;
      fetch(url, {
        method: "POST",
        cache: "no-cache",
        headers: {
          "Content-Type": "application/json",
          "Csrf-Token": token,
        },
        body: JSON.stringify(payload),
      }).then((response) => {
        if (response.ok) {
          return response.json();
        } else {
          // Server error (= logged by Sentry)
          serverErrorMessage?.classList.remove("hidden");
          ajaxRequestIsRunning = false;
          return null;
        }
      }).then((mandat: Mandat) => {
        if (mandat) {
          ajaxRequestIsRunning = false;
          lastMandatGenerationData = { form: payload, mandat: mandat };
          const mandatUrl: string = mandatPageUrl(mandat);

          successLink?.classList.remove("hidden");
          const link = successLink?.querySelector("a");
          if (link) {
            link.href = mandatUrl;
          }
          if (linkedMandatInput) {
            linkedMandatInput.value = mandat.id;
          }

          window.open(mandatUrl, "_blank");
        }
      }).catch((e) => {
        // Browser error (= not logged by Sentry)
        console.error(e);
        browserErrorMessage?.classList.remove("hidden");
        ajaxRequestIsRunning = false;
      });

    } else {
      validationFailedMessage?.classList.remove("hidden");
    }
  }



  setupFormListeners();
  setupMandatTypeRadioListeners();

}



setupDynamicUsagerInfosButtons();
setupInvitedGroups();
setupMandatForm();
