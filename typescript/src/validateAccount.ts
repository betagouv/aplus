const firstnameValue = <HTMLInputElement>document.getElementById('firstnameValue');
const lastnameValue = <HTMLInputElement>document.getElementById('lastnameValue');
const qualiteValue = <HTMLInputElement>document.getElementById('qualiteValue');
const sharedAccountValue = <HTMLInputElement>document.getElementById('sharedAccountValue');

const checkbox = <HTMLInputElement>document.querySelector("#checkbox-charte");

function formCanBeSubmitted(sharedAccount: Boolean) {
    return checkbox.checked && (sharedAccount ? true :
        firstnameValue.value.trim() !== '' &&
        lastnameValue.value.trim() !== '' &&
        qualiteValue.value.trim() !== '');
}

function addInputEvent(el: Element, sharedAccount: Boolean) {
    el.addEventListener(`input`, function () {
        const e = <HTMLInputElement>document.querySelector("#validation")
        e.disabled = !formCanBeSubmitted(sharedAccount);
    });
}

function addClickEvent(el: Element, sharedAccount: Boolean) {
    el.addEventListener(`click`, function () {
        const e = <HTMLInputElement>document.querySelector("#validation");
        e.disabled = !formCanBeSubmitted(sharedAccount);
    });
}

const sharedAccount = sharedAccountValue.value !== "false";

addClickEvent(checkbox, sharedAccount);
if (!sharedAccount) {
    addInputEvent(firstnameValue, sharedAccount);
    addInputEvent(lastnameValue, sharedAccount);
    addInputEvent(qualiteValue, sharedAccount);
}