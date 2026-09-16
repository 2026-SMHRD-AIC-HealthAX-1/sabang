document.querySelectorAll(".tab-btn").forEach(button => {

    button.addEventListener("click", function() {

        const target = button.dataset.tab;

        document.querySelectorAll(".tab-btn").forEach(btn => {
            btn.classList.toggle("active", btn === button);
            btn.setAttribute("aria-selected", btn === button ? "true" : "false");
        });

        document.querySelectorAll(".tab-panel").forEach(panel => {
            panel.hidden = panel.dataset.panel !== target;
        });
    });
});
