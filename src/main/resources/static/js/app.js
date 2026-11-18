document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-table-search]').forEach((input) => {
    const target = document.querySelector(input.getAttribute('data-table-search'));
    if (!target) return;
    input.addEventListener('input', () => {
      const term = input.value.trim().toLocaleLowerCase();
      target.querySelectorAll('tbody tr').forEach((row) => {
        row.hidden = !row.innerText.toLocaleLowerCase().includes(term);
      });
    });
  });
});

// ---- v3: mobile menu, confirm dialogs, print (inline handlers are blocked by the Content-Security-Policy)
document.addEventListener('click', (e) => {
  if (e.target.closest('[data-menu-toggle]')) { document.body.classList.toggle('sidebar-open'); return; }
  if (e.target.closest('[data-menu-close]') || e.target.closest('.side-nav a')) { document.body.classList.remove('sidebar-open'); }
  if (e.target.closest('[data-print]')) { window.print(); }
});
document.addEventListener('submit', (e) => {
  const msg = e.target.getAttribute && e.target.getAttribute('data-confirm');
  if (msg && !window.confirm(msg)) e.preventDefault();
});

// ---- credentials form: show the academic-only fields just for academic credential types
document.addEventListener('DOMContentLoaded', () => {
  const sel = document.getElementById('credType');
  if (!sel) return;
  const academic = ['ACADEMIC_DEGREE', 'ACADEMIC_CERTIFICATE'];
  const toggle = () => document.querySelectorAll('.academic').forEach((el) => {
    el.style.display = academic.includes(sel.value) ? '' : 'none';
  });
  sel.addEventListener('change', toggle);
  toggle();
});

// ---- department -> unit dropdowns: show only the units of the chosen department (the server re-checks this too)
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('form').forEach((form) => {
    const dept = form.querySelector('[data-dept-select]');
    const unit = form.querySelector('[data-unit-select]');
    if (!dept || !unit) return;
    const sync = () => {
      const d = dept.value;
      Array.from(unit.options).forEach((o) => {
        const match = !o.value || !d || o.getAttribute('data-dept') === d;
        o.hidden = !match;
        o.disabled = !match;
      });
      const chosen = unit.selectedOptions[0];
      if (chosen && chosen.disabled) unit.value = '';
    };
    dept.addEventListener('change', sync);
    unit.addEventListener('change', () => {
      const o = unit.selectedOptions[0];
      if (o && o.value && !dept.value) { dept.value = o.getAttribute('data-dept'); sync(); }
    });
    sync();
  });
});
