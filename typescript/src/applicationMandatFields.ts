//
// Application Form - Mandat Radio
//

function mandatFieldsAreFilled(): boolean {
  // 1. Checkbox
  const checkbox = <HTMLInputElement | null>document.getElementById("checkbox-mandat");
  if (checkbox) {
    if (!checkbox.checked) {
      return false;
    }
  }

  // 2. Radios
  const radios = document.querySelectorAll<HTMLInputElement>("[id^=mandatType]");
  // Note: same as oneRadioIsChecked = radios.exists(r => r.checked)
  let oneRadioIsChecked = false;
  radios.forEach((radio) => {
    if (radio.checked) {
      oneRadioIsChecked = true;
    }
  });
  if (!oneRadioIsChecked) {
    return false;
  }

  // 3. Date input
  const dateInput = <HTMLInputElement | null>document.getElementById("mandatDate");
  if (dateInput && !dateInput.value) {
    return false;
  }

  return true;
}

function onMandatFieldChange() {
  const validation = document.querySelector<HTMLButtonElement>("#review-validation");
  if (validation != null) {
    if (mandatFieldsAreFilled()) {
      validation.disabled = false;
    } else {
      validation.disabled = true;
    }
  }
}



// Mandat
const checkboxMandat = document.getElementById("checkbox-mandat");
const radiosMandat = document.querySelectorAll("[id^=mandatType]");
const dateMandat = document.getElementById("mandatDate");
if (checkboxMandat) {
  checkboxMandat.addEventListener('change', onMandatFieldChange);
}
radiosMandat.forEach((radio) => {
  radio.addEventListener('change', onMandatFieldChange);
});
if (dateMandat) {
  dateMandat.addEventListener('change', onMandatFieldChange);
  dateMandat.addEventListener('keyup', onMandatFieldChange);
  dateMandat.addEventListener('paste', onMandatFieldChange);
  dateMandat.addEventListener('input', onMandatFieldChange);
}
