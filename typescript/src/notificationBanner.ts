//
// Notification Banners (mainly after using a flash cookie)
//
// Will add onClick listeners on `.notification__close-btn` that remove the `.notification` element
//



function onClickRemoveElement(clickElement: Element, elementToRemove: Element) {
  clickElement.addEventListener("click", () => {
    // Cross browser
    // https://stackoverflow.com/questions/3387427/remove-element-by-id
    elementToRemove.outerHTML = "";
  });
}

document.querySelectorAll(".notification").forEach((elem) => {
  const closeBtn = elem.querySelector(".notification__close-btn");
  if (closeBtn != null) {
    onClickRemoveElement(closeBtn, elem);
  }
});
