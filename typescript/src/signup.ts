const groupsDatasetFieldId = "signup-groups-id";
// CamelCased due to spec
const groupsDataset = "signupGroups";
const signupOrganisationFieldId = "signup-organisation-id";
const signupAreaFieldId = "signup-area-id";
const signupGroupFieldId = "signup-group-id";

const sharedAccountRadioId = "signup-shared-account-radio-id"
const personalAccountRadioId = "signup-personal-account-radio-id"
const sharedAccountFieldsId = "signup-shared-account-fields-id"
const personalAccountFieldsId = "signup-personal-account-fields-id"
const firstNameId = "signup-first-name-id"
const lastNameId = "signup-last-name-id"
const qualiteId = "signup-qualite-id"
const sharedAccountNameId = "signup-shared-account-name-id"



interface UserGroup {
  id: string;
  name: string;
  organisationId: string;
  areaId: string;
}

interface IndexedGroups {
  [organisation: string]: { [area: string]: Array<UserGroup> }
}



// Setup the select for groups (just filters the groups according to area and organisation)
function setupSignupGroupSelect() {

  const organisationInput = <HTMLSelectElement | null>document.getElementById(signupOrganisationFieldId);
  const areaInput = <HTMLSelectElement | null>document.getElementById(signupAreaFieldId);
  const groupInput = <HTMLSelectElement | null>document.getElementById(signupGroupFieldId);
  const groupsDataField = <HTMLElement | null>document.getElementById(groupsDatasetFieldId);

  if (organisationInput != null && areaInput != null && groupInput != null && groupsDataField != null) {
    const unparsedGroups = groupsDataField.dataset[groupsDataset];
    if (unparsedGroups == null) {
      return;
    }
    const groups: Array<UserGroup> = JSON.parse(unparsedGroups);
    const indexedGroups: IndexedGroups = {};
    groups.forEach(group => {
      const indexedGroup = indexedGroups[group.organisationId];
      if (indexedGroup != null) {
        if (indexedGroup[group.areaId]) {
          indexedGroup[group.areaId]?.push(group);
        } else {
          indexedGroup[group.areaId] = [group];
        }
      } else {
        const index: { [area: string]: Array<UserGroup> } = {};
        index[group.areaId] = [group];
        indexedGroups[group.organisationId] = index;
      }
    });
    // Initial setup
    setGroupsOptions(organisationInput, areaInput, groupInput, indexedGroups);
    // Change when org or area changed
    organisationInput.addEventListener("change", () =>
      setGroupsOptions(organisationInput, areaInput, groupInput, indexedGroups));
    areaInput.addEventListener("change", () =>
      setGroupsOptions(organisationInput, areaInput, groupInput, indexedGroups));
  }

}

function setGroupsOptions(
  organisationInput: HTMLSelectElement,
  areaInput: HTMLSelectElement,
  groupInput: HTMLSelectElement,
  groups: IndexedGroups
) {
  const selectedGroupId = (groupInput.value ||
    (groupInput.options[groupInput.selectedIndex] && groupInput.options[groupInput.selectedIndex]?.value));
  let selectableGroups: Array<UserGroup> = [];
  const groupsInThisOrganisation = groups[organisationInput.value];
  if (groupsInThisOrganisation != null) {
    const groups = groupsInThisOrganisation[areaInput.value];
    if (groups != null) {
      selectableGroups = groups;
    }
  }

  groupInput.innerHTML = "";
  let optionElem = document.createElement('option');
  optionElem.value = "";
  optionElem.textContent = "Sélectionnez votre structure";
  groupInput.appendChild(optionElem);

  selectableGroups.forEach(group => {
    let optionElem = document.createElement('option');
    optionElem.value = group.id;
    optionElem.textContent = group.name;
    if (group.id === selectedGroupId) {
      optionElem.selected = true;
    }
    groupInput.appendChild(optionElem);
  });
}



function setupSignupSharedAccountRadio() {

  const sharedAccountRadio = <HTMLInputElement | null>document.getElementById(sharedAccountRadioId);
  const personalAccountRadio = <HTMLInputElement | null>document.getElementById(personalAccountRadioId);
  const sharedAccountFields = <HTMLElement | null>document.getElementById(sharedAccountFieldsId);
  const personalAccountFields = <HTMLElement | null>document.getElementById(personalAccountFieldsId);
  const sharedAccountName = <HTMLInputElement | null>document.getElementById(sharedAccountNameId);
  const firstName = <HTMLInputElement | null>document.getElementById(firstNameId);
  const lastName = <HTMLInputElement | null>document.getElementById(lastNameId);
  const qualite = <HTMLInputElement | null>document.getElementById(qualiteId);

  if (
    sharedAccountRadio != null &&
    personalAccountRadio != null &&
    sharedAccountFields != null &&
    personalAccountFields != null &&
    firstName != null &&
    lastName != null &&
    qualite != null &&
    sharedAccountName != null
  ) {
    setSharedAccountFields(
      sharedAccountRadio.checked,
      sharedAccountFields,
      personalAccountFields,
      sharedAccountName,
      firstName,
      lastName,
      qualite
    );
    sharedAccountRadio.addEventListener("change", () =>
      setSharedAccountFields(
        sharedAccountRadio.checked,
        sharedAccountFields,
        personalAccountFields,
        sharedAccountName,
        firstName,
        lastName,
        qualite
      )
    );
    personalAccountRadio.addEventListener("change", () =>
      setSharedAccountFields(
        sharedAccountRadio.checked,
        sharedAccountFields,
        personalAccountFields,
        sharedAccountName,
        firstName,
        lastName,
        qualite
      )
    );
  }
}

function setSharedAccountFields(
  isSharedAccount: boolean,
  sharedAccountFields: HTMLElement,
  personalAccountFields: HTMLElement,
  sharedAccountName: HTMLInputElement,
  firstName: HTMLInputElement,
  lastName: HTMLInputElement,
  qualite: HTMLInputElement
) {
  if (isSharedAccount) {
    sharedAccountFields.classList.remove("hidden-important");
    personalAccountFields.classList.add("hidden-important");
    firstName.value = "";
    lastName.value = "";
    qualite.value = "";
  } else {
    sharedAccountFields.classList.add("hidden-important");
    personalAccountFields.classList.remove("hidden-important");
    sharedAccountName.value = ""
  }
}




setupSignupGroupSelect();
setupSignupSharedAccountRadio();
