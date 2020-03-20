// Note: this file is temporary, everything in main.js should be at the bottom of the page

//
// Transform <select> with SlimSelect
//

var slimSelectClass = "use-slimselect"

Array.from(document.querySelectorAll("." + slimSelectClass)).forEach(function (select) {
  new SlimSelect({ select: select })
});
