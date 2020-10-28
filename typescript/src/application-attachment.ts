interface ApplicationAttachment {
  list: HTMLElement
  fileInputCount: number
  idSequence: number
  increaseOrDecreaseFileInputList: (EventListener) => any
}

let attachment: any = {}


const createAttachment = (list: HTMLElement, count: number, sequence: number): ApplicationAttachment => {
  return {
    list,
    fileInputCount: count,
    idSequence: sequence, // In order to avoid id collisions
    increaseOrDecreaseFileInputList: event => {
      if (event.target.files.length > 0) {
        // If a new file is added, add a new file input.
        const li: HTMLLIElement = document.createElement("li");
        const input: HTMLInputElement = document.createElement("input");
        input.setAttribute("type", "file");
        input.setAttribute("name", "file[" + (attachment.idSequence++) + "]");
        attachment.fileInputCount++;
        input.addEventListener("change", attachment.increaseOrDecreaseFileInputList);
        attachment.list.appendChild(li);
        li.appendChild(input);
      } else {
        // If a previous file is unset for upload, remove the corresponding input file.
        if (attachment.fileInputCount > 1) {
          event.target.parentElement.parentElement.removeChild(event.target.parentElement);
          attachment.fileInputCount--;
        }
      }
    }
  }
}

const main = (list: HTMLElement | null) => {
  if (list != null) {
    const attachment = createAttachment(list, 1, 0)
    // For all file inputs, add a listener that adds a new file input to allow
    // for more attachments to be made.
    attachment.list.querySelectorAll("input").forEach(input => {
      input.addEventListener("change", attachment.increaseOrDecreaseFileInputList);
    });

    return attachment;
  }
}

window.document.addEventListener("DOMContentLoaded", _ => {
  "use strict";
  const attachmentList: HTMLElement | null = document.getElementById("attachment-list");
  attachment = main(attachmentList)
}, false);
