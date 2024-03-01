const attachmentList: HTMLElement | null = document.getElementById("attachment-list");

if (attachmentList) {

  let fileInputCount: number = 1;
  let idSequence: number = 1; // In order to avoid id collisions
  let increaseOrDecreaseFileInputList: (event: Event) => void = () => { };

  increaseOrDecreaseFileInputList = (event) => {
    const target = event.target as HTMLInputElement;
    if (target.files && target.files.length > 0) {
      // If a new file is added, add a new file input.
      const li: HTMLLIElement = document.createElement("li");
      const input: HTMLInputElement = document.createElement("input");
      input.setAttribute("type", "file");
      input.setAttribute("name", "file[" + (idSequence++) + "]");
      fileInputCount++;
      input.addEventListener("change", increaseOrDecreaseFileInputList);
      attachmentList.appendChild(li);
      li.appendChild(input);
    } else {
      // If a previous file is unset for upload, remove the corresponding input file.
      if (fileInputCount > 1) {
        const parent = target.parentElement;
        if (parent != null) {
          parent.parentElement?.removeChild(parent);
          fileInputCount--;
        }
      }
    }
  };

  // For all file inputs, add a listener that adds a new file input to allow
  // for more attachments to be made.
  attachmentList.querySelectorAll("input").forEach((input) => {
    input.addEventListener("change", increaseOrDecreaseFileInputList);
  });
}
