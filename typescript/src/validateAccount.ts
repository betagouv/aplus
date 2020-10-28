const firstnameValue = <HTMLInputElement | null>document.getElementById('firstnameValue');
const lastnameValue = <HTMLInputElement | null>document.getElementById('lastnameValue');
const qualiteValue = <HTMLInputElement | null>document.getElementById('qualiteValue');
const sharedAccountValue = <HTMLInputElement | null>document.getElementById('sharedAccountValue');

const checkbox = <HTMLInputElement | null>document.querySelector("#checkbox-charte");

const formCanBeSubmitted = (sharedAccount: Boolean) => {
    return checkbox && checkbox.checked && (sharedAccount ? true :
        firstnameValue && firstnameValue.value.trim() !== '' &&
        lastnameValue && lastnameValue.value.trim() !== '' &&
        qualiteValue && qualiteValue.value.trim() !== '');
}

const addInputEvent = (el: Element, sharedAccount: Boolean) => {
    el && el.addEventListener(`input`, () => {
        const e = <HTMLInputElement | null>document.querySelector("#validation")
        e.disabled = !formCanBeSubmitted(sharedAccount);
    });
}

const addClickEvent = (el: Element, sharedAccount: Boolean) => {
    el.addEventListener(`click`, () => {
        const e = <HTMLInputElement | null>document.querySelector("#validation");
        e.disabled = !formCanBeSubmitted(sharedAccount);
    });
}

const sharedAccount = sharedAccountValue && sharedAccountValue.value !== "false";

checkbox && addClickEvent(checkbox, sharedAccount);

if (!sharedAccount) {
    firstnameValue && addInputEvent(firstnameValue, sharedAccount);
    lastnameValue && addInputEvent(lastnameValue, sharedAccount);
    qualiteValue && addInputEvent(qualiteValue, sharedAccount);
}