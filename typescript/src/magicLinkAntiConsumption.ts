const magicLinkId = "magic-link-anti-consumption";

let redirected = false;

const magicLinkElement = <HTMLLinkElement | null>document.getElementById(magicLinkId);

if (magicLinkElement) {
  const redirect = (redirectUrl: string) => {
    if (!redirected) {
      magicLinkElement.disabled = true;
      redirected = true;
      window.location.href = redirectUrl;
    }
  };

  const redirectUrl = magicLinkElement.dataset["redirectUrl"];
  if (redirectUrl) {
    window.setTimeout(() => redirect(redirectUrl), 1500);
  }
}
