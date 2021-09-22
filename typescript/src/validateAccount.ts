const firstnameValue = <HTMLInputElement | null>document.getElementById('firstnameValue');
const lastnameValue = <HTMLInputElement | null>document.getElementById('lastnameValue');
const qualiteValue = <HTMLInputElement | null>document.getElementById('qualiteValue');
const sharedAccountValue = <HTMLInputElement | null>document.getElementById('sharedAccountValue');

const checkbox = <HTMLInputElement | null>document.querySelector("#checkbox-charte");

const formCanBeSubmitted = (sharedAccount: boolean) => {
  return checkbox && checkbox.checked && (sharedAccount ? true :
    firstnameValue && firstnameValue.value.trim() !== '' &&
    lastnameValue && lastnameValue.value.trim() !== '' &&
    qualiteValue && qualiteValue.value.trim() !== '');
}

const addInputEvent = (el: Element, sharedAccount: boolean) => {
  el && el.addEventListener(`input`, () => {
    const e = <HTMLInputElement | null>document.querySelector("#validation")
    if (e != null) {
      e.disabled = !formCanBeSubmitted(sharedAccount);
    }
  });
}

const addClickEvent = (el: Element, sharedAccount: boolean) => {
  el.addEventListener(`click`, () => {
    const e = <HTMLInputElement | null>document.querySelector("#validation");
    if (e != null) {
      e.disabled = !formCanBeSubmitted(sharedAccount);
    }
  });
}

const sharedAccount = sharedAccountValue != null && sharedAccountValue.value !== "false";

checkbox && addClickEvent(checkbox, sharedAccount);

if (!sharedAccount) {
  firstnameValue && addInputEvent(firstnameValue, sharedAccount);
  lastnameValue && addInputEvent(lastnameValue, sharedAccount);
  qualiteValue && addInputEvent(qualiteValue, sharedAccount);
}
