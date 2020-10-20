const firstnameValue = <HTMLInputElement>document.getElementById('firstnameValue');
const lastnameValue = <HTMLInputElement>document.getElementById('lastnameValue');

const checkbox = <HTMLInputElement>document.querySelector("#checkbox-charte");

function formCanBeSubmitted() {
    return checkbox.checked &&
        firstnameValue.value.trim() !== '' &&
        lastnameValue.value.trim() !== '';
}

function addInputEvent(el: Element) {
    el.addEventListener(`input`, function () {
        const e = <HTMLInputElement>document.querySelector("#validation")
        e.disabled = !formCanBeSubmitted();
    });
}

function addClickEvent(el: Element) {
    el.addEventListener(`click`, function () {
        const e = <HTMLInputElement>document.querySelector("#validation");
        e.disabled = !formCanBeSubmitted();
    });
}

addClickEvent(checkbox);
addInputEvent(firstnameValue);
addInputEvent(lastnameValue);