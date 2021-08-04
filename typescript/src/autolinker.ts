import Autolinker from 'autolinker';

const autolinkerClass = "use-autolinker";


const elements: Array<HTMLElement> = Array.from(document.querySelectorAll("." + autolinkerClass));

if (elements.length > 0) {

  // http://greg-jacobs.com/Autolinker.js/examples/index.html
  const autolinker = new Autolinker({
    urls: {
      schemeMatches: true,
      wwwMatches: true,
      tldMatches: true
    },
    email: false,
    phone: false,
    mention: false,
    hashtag: false,

    stripPrefix: false,
    stripTrailingSlash: false,

    // adds target="_blank" rel="noopener noreferrer"
    // https://github.com/gregjacobs/Autolinker.js/blob/v3.14.3/src/anchor-tag-builder.ts#L101
    newWindow: true,

    truncate: {
      length: 0,
      location: 'end'
    },

    className: ''
  });

  elements.forEach((element) => {
    if (element.innerText) {
      const innerHtml = autolinker.link(element.innerText);
      element.innerHTML = innerHtml;
    }
  });

}
