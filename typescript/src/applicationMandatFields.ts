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

if (checkboxMandat) {
  checkboxMandat.addEventListener('change', onMandatFieldChange);
}
