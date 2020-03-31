// Note: this file is temporary, everything in main.js should be at the bottom of the page

//
// Transform <select> with SlimSelect
//

var slimSelectClass = "use-slimselect"

Array.from(document.querySelectorAll("." + slimSelectClass)).forEach(function (select) {
  new SlimSelect({ select: select })
});


//
// Fix Accessibility for tooltips
//
// see https://stackoverflow.com/questions/45364249/tooltip-accessibility
// https://www.w3.org/TR/wai-aria-practices/#tooltip
// https://github.com/nico3333fr/van11y-accessible-simple-tooltip-aria/blob/master/src/van11y-accessible-simple-tooltip-aria.es6.js
// https://github.com/google/material-design-lite/blob/mdl-1.x/src/tooltip/tooltip.js
// TODO: the tooltip should be visible when there is a focus event

Array.from(document.querySelectorAll(".mdl-tooltip")).forEach(function (tooltip) {
  var elementId = tooltip.getAttribute("for") || tooltip.getAttribute("data-mdl-for");
  if (elementId) {
    var element = document.getElementById(elementId);
    ["mouseenter", "focus"].forEach(function (eventName) {
      element.addEventListener(eventName, function (e) {
        tooltip.setAttribute("aria-hidden", "false");
      });
    });
    ["mouseleave", "blur"].forEach(function (eventName) {
      element.addEventListener(eventName, function (e) {
        tooltip.setAttribute("aria-hidden", "true");
      });
    });
  }
});
