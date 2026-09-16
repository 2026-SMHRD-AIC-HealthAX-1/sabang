document.getElementById("view-receipt").addEventListener("click", () => {
    const details = document.getElementById("receipt-details");
    details.focus({ preventScroll: true });
    details.scrollIntoView({
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
        block: "nearest"
    });
});
