// Note: this file is temporary, everything in main.js should be at the bottom of the page
// TODO: The idea is to put everything in main.js in this file, then rename this file as main.js



//
// Area Change Select
//

function setupChangeAreaSelect() {
  var select = document.getElementById("changeAreaSelect");
  if (select) {
    var currentArea = select.dataset["currentArea"];
    var redirectUrlPrefix = select.dataset["redirectUrlPrefix"];
    select.addEventListener('change', function() {
      var selectedArea = select.value;
      if(selectedArea != currentArea) {
        document.location = redirectUrlPrefix+selectedArea;
      }
    });
  }
}

setupChangeAreaSelect()

//
// Application form
//

function mandatFieldsAreFilled() {
  // 1. Checkbox
  var checkbox = document.getElementById("checkbox-mandat");
  if (checkbox) {
    if (!checkbox.checked) {
      return false;
    }
  }

  // 2. Radios
  var radios = document.querySelectorAll("[id^=mandatType]");
  // Note: same as oneRadioIsChecked = radios.exists(r => r.checked)
  var oneRadioIsChecked = false;
  for (var i = 0; i < radios.length; i++) {
    if (radios[i].checked) {
      oneRadioIsChecked = true;
    }
  }
  if (!oneRadioIsChecked) {
    return false;
  }

  // 3. Date input
  var dateInput = document.getElementById("mandatDate");
  if (!dateInput.value) {
    return false;
  }

  return true;
}

function onMandatFieldChange() {
  if (mandatFieldsAreFilled()) {
    document.querySelector("#review-validation").disabled = false;
  } else {
    document.querySelector("#review-validation").disabled = true;
  }
}

function setupApplicationForm() {

  // Mandat
  var checkboxMandat = document.getElementById("checkbox-mandat");
  var radiosMandat = document.querySelectorAll("[id^=mandatType]");
  var dateMandat = document.getElementById("mandatDate");
  if (checkboxMandat) {
    checkboxMandat.addEventListener('change', onMandatFieldChange);
  }
  for (var i = 0; i < radiosMandat.length; i++) {
    radiosMandat[i].addEventListener('change', onMandatFieldChange);
  }
  if (dateMandat) {
    dateMandat.addEventListener('change', onMandatFieldChange);
    dateMandat.addEventListener('keyup', onMandatFieldChange);
    dateMandat.addEventListener('paste', onMandatFieldChange);
    dateMandat.addEventListener('input', onMandatFieldChange);
  }

}

setupApplicationForm();



//
// Transform <select> with SlimSelect
//

var slimSelectClass = "use-slimselect";

Array.from(document.querySelectorAll("." + slimSelectClass)).forEach(function (select) {
  new SlimSelect({ select: select, selectByGroup: true, closeOnSelect: false});
});



//
// Welcome Page
//

var welcomePageNewletterCheckboxTagId = "aplus-welcome-page-newsletter-checkbox"
var welcomePageNewletterSubmitTagId = "aplus-welcome-page-newsletter-submit"
var welcomePageNewletterCheckbox = document.querySelector("#" + welcomePageNewletterCheckboxTagId);
if (welcomePageNewletterCheckbox != null) {
  welcomePageNewletterCheckbox.addEventListener('click', function() {
    if (welcomePageNewletterCheckbox.checked) {
      document.querySelector("#" + welcomePageNewletterSubmitTagId).disabled = false;
    } else {
      document.querySelector("#" + welcomePageNewletterSubmitTagId).disabled = true;
    }
  });
}
