var firstnameValue = <HTMLInputElement | null>document.getElementById('firstnameValue');
var lastnameValue = <HTMLInputElement | null>document.getElementById('lastnameValue');
var qualiteValue = <HTMLInputElement | null>document.getElementById('qualiteValue');
var sharedAccountValue = <HTMLInputElement | null>document.getElementById('sharedAccountValue');

var checkbox = <HTMLInputElement | null>document.querySelector("#checkbox-charte");

function formCanBeSubmitted(sharedAccount: Boolean) {
    return checkbox && checkbox.checked && (sharedAccount ? true :
        firstnameValue && firstnameValue.value.trim() !== '' &&
        lastnameValue && lastnameValue.value.trim() !== '' &&
        qualiteValue && qualiteValue.value.trim() !== '');
}

function addInputEvent(el: Element, sharedAccount: Boolean) {
    el && el.addEventListener(`input`, function () {
        var e = <HTMLInputElement | null>document.querySelector("#validation")
        e.disabled = !formCanBeSubmitted(sharedAccount);
    });
}

function addClickEvent(el: Element, sharedAccount: Boolean) {
    el.addEventListener(`click`, function () {
        var e = <HTMLInputElement | null>document.querySelector("#validation");
        e.disabled = !formCanBeSubmitted(sharedAccount);
    });
}

var sharedAccount = sharedAccountValue && sharedAccountValue.value !== "false";

checkbox && addClickEvent(checkbox, sharedAccount);

if (!sharedAccount) {
    firstnameValue && addInputEvent(firstnameValue, sharedAccount);
    lastnameValue && addInputEvent(lastnameValue, sharedAccount);
    qualiteValue && addInputEvent(qualiteValue, sharedAccount);
}