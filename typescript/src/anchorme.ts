import anchorme from "anchorme"
import {Options} from "anchorme/dist/node/types";

const options: Partial<Options> = {
    attributes: {
        target: "_blank",
        rel: "nofollow noreferrer noopener"
    },
}

const descriptions: HTMLCollectionOf<HTMLElement> = document.getElementsByClassName("application__message") as HTMLCollectionOf<HTMLElement>

Array.from(descriptions).forEach((description) => {
    const content: string | null = description.innerText ? anchorme({input: description.innerText, options}) : null
    if (content) {
        description.innerHTML = content
    }
});