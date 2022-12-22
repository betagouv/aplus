function setupMandatPrintButton() {

  const printButton = <HTMLButtonElement>document
    .getElementById("print-mandat-button");

  printButton?.addEventListener('click', () => { window.print(); });

}


function setupAutoPrint() {

  if (window.location.pathname.startsWith("/mandats/")) {
    window.addEventListener("load", () => {
      if (window.location.hash.includes("impression-automatique")) {
        setTimeout(() => {
          window.print();
        }, 1000);
      }
    });
  }

}


setupMandatPrintButton();
setupAutoPrint();
