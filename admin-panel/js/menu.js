import { db } from './firebase-config.js';
import {
  collection,
  doc,
  addDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  onSnapshot,
  serverTimestamp,
  writeBatch,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import {
  escapeHtml,
  toast,
  showConnectionError,
  hideConnectionError,
  openModal,
  closeModal,
  setupModalClose,
} from './utils.js';
import { paths, logFirestoreWrite, logFirestoreListen } from './paths.js';
import { getHotelId, onHotelChange, onHotelMetaChange, isCorporateProperty } from './tenant-context.js';

const DEFAULT_CATEGORIES = [
  { key: 'starters', label: 'Starters' },
  { key: 'main_course', label: 'Main Course' },
  { key: 'beverages', label: 'Beverages' },
  { key: 'desserts', label: 'Desserts' },
];

const CATEGORY_ICONS = {
  starters: '🥗',
  main_course: '🍛',
  beverages: '🥤',
  desserts: '🍰',
  breakfast: '🍳',
  snacks: '🥪',
};

let menuItems = [];
let categories = [...DEFAULT_CATEGORIES];
let activeFilter = 'all';
let editingItemId = null;
let settingsSeeded = false;
let menuSettingsUnsub = null;
let menuUnsub = null;

/** Parsed rows ready for import (valid only). */
let bulkParsedRows = [];
let bulkImporting = false;

const SAMPLE_CSV = `category,name,price,description,is_veg,image_url
starters,Paneer Tikka,280,Grilled cottage cheese with spices,true,
starters,Chicken Seekh Kebab,320,Minced chicken skewers,false,
main_course,Butter Chicken,450,Creamy tomato gravy with chicken,false,
main_course,Aloo Mutter,280,Potato and peas curry,true,
beverages,Masala Chai,80,Spiced Indian tea,true,
beverages,Fresh Lime Soda,120,Sweet or salted,true,
desserts,Gulab Jamun,150,Warm milk dumplings in syrup,true,
desserts,Chocolate Brownie,200,Warm brownie with ice cream,true,
`;

const REQUIRED_COLUMNS = ['category', 'name', 'price'];
const BATCH_LIMIT = 400;

export function initMenu() {
  renderFilterTabs();
  setupMenuItemModal();
  setupAddCategoryModal();
  setupBulkUpload();
  applyMenuPriceFieldVisibility();
  onHotelChange(() => {
    settingsSeeded = false;
    applyMenuPriceFieldVisibility();
    listenMenuSettings();
    listenMenu();
  });
  onHotelMetaChange(() => {
    applyMenuPriceFieldVisibility();
  });
}

function listenMenuSettings() {
  if (menuSettingsUnsub) {
    menuSettingsUnsub();
    menuSettingsUnsub = null;
  }

  const hotelId = getHotelId();
  if (!hotelId) {
    categories = [...DEFAULT_CATEGORIES];
    renderFilterTabs();
    populateCategorySelect();
    return;
  }

  const docPath = paths.menuSettingsDoc();
  logFirestoreListen('Menu Settings', docPath);

  menuSettingsUnsub = onSnapshot(
    doc(db, 'Hotels', hotelId, 'Config', 'menuSettings'),
    async (snapshot) => {
      if (!snapshot.exists()) {
        if (!settingsSeeded) {
          settingsSeeded = true;
          await seedMenuSettings();
        }
        return;
      }

      const data = snapshot.data();
      if (Array.isArray(data.categories) && data.categories.length) {
        categories = data.categories;
      } else {
        categories = [...DEFAULT_CATEGORIES];
      }
      renderFilterTabs();
      populateCategorySelect();
    },
    (err) => {
      console.error('[Firestore ERROR] Menu settings listener:', err);
    },
  );
}

async function seedMenuSettings() {
  const hotelId = getHotelId();
  if (!hotelId) return;
  try {
    const payload = {
      categories: DEFAULT_CATEGORIES,
      updatedAt: serverTimestamp(),
    };
    await setDoc(doc(db, 'Hotels', hotelId, 'Config', 'menuSettings'), payload, { merge: true });
    logFirestoreWrite('Menu Settings Seed', paths.menuSettingsDoc(), payload);
  } catch (err) {
    console.error('[Firestore ERROR] Menu settings seed failed:', err);
  }
}

function listenMenu() {
  if (menuUnsub) {
    menuUnsub();
    menuUnsub = null;
  }

  const hotelId = getHotelId();
  if (!hotelId) {
    menuItems = [];
    renderMenu();
    return;
  }

  logFirestoreListen('Menu', paths.menuCollection());

  menuUnsub = onSnapshot(
    collection(db, 'Hotels', hotelId, 'Menu'),
    (snapshot) => {
      hideConnectionError();
      menuItems = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .sort((a, b) => {
          const cat = (a.category || '').localeCompare(b.category || '');
          if (cat !== 0) return cat;
          return (a.name || '').localeCompare(b.name || '');
        });
      renderMenu();
    },
    (err) => {
      console.error('[Firestore ERROR] Menu listener:', err);
      showConnectionError('Could not load menu from Firestore.');
    },
  );
}

function renderFilterTabs() {
  const container = document.getElementById('menu-filter-tabs');
  if (!container) return;

  const tabs = [
    { key: 'all', label: 'All' },
    ...categories.map((c) => ({ key: c.key, label: c.label })),
  ];

  container.innerHTML = tabs
    .map(
      (tab) =>
        `<button class="filter-tab${activeFilter === tab.key ? ' active' : ''}" data-filter="${escapeHtml(tab.key)}">${escapeHtml(tab.label)}</button>`,
    )
    .join('');

  container.querySelectorAll('.filter-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      activeFilter = tab.dataset.filter;
      renderFilterTabs();
      renderMenu();
    });
  });
}

function getCategoryLabel(key) {
  return categories.find((c) => c.key === key)?.label || formatLegacyCategory(key);
}

function formatLegacyCategory(key) {
  if (!key) return 'Uncategorized';
  return key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

function getFilteredItems() {
  if (activeFilter === 'all') return menuItems;
  return menuItems.filter((item) => item.category === activeFilter);
}

function renderMenu() {
  const container = document.getElementById('menu-list');
  const countBadge = document.getElementById('menu-count');
  const items = getFilteredItems();

  if (countBadge) countBadge.textContent = menuItems.length;

  if (!container) return;

  if (!menuItems.length) {
    container.innerHTML = `
      <p class="empty-state col-span-full">
        No menu items yet. Click <strong>+ Add Food Item</strong> to create your first dish.
      </p>`;
    return;
  }

  if (!items.length) {
    container.innerHTML = `
      <p class="empty-state col-span-full">
        No items in this category. Try another filter or add a new dish.
      </p>`;
    return;
  }

  container.innerHTML = items.map(renderMenuCard).join('');
  bindMenuActions(container);
}

function renderMenuCard(item) {
  const inStock = item.available !== false;
  const categoryKey = item.category || 'starters';
  const categoryLabel = getCategoryLabel(categoryKey);
  const icon = CATEGORY_ICONS[categoryKey] || '🍽️';
  const imageBlock = item.imageUrl
    ? `<img src="${escapeHtml(item.imageUrl)}" alt="" class="menu-card-img" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" /><span class="menu-card-icon" style="display:none">${icon}</span>`
    : `<span class="menu-card-icon">${icon}</span>`;

  return `
    <div class="menu-card${inStock ? '' : ' menu-card-out'}" data-searchable data-search-text="${escapeHtml(item.name || '')} ${escapeHtml(categoryLabel)} ${escapeHtml(item.description || '')}">
      <div class="menu-card-header">
        ${imageBlock}
        <div class="menu-card-info">
          <div class="menu-card-title-row">
            <h4 class="menu-card-name">${escapeHtml(item.name || 'Untitled')}</h4>
            <span class="menu-category-badge">${escapeHtml(categoryLabel)}</span>
          </div>
          <p class="menu-card-desc">${escapeHtml(item.description || 'No description')}</p>
        </div>
      </div>
      <div class="menu-card-footer">
        <span class="menu-card-price">₹${(item.price || 0).toFixed(0)}</span>
        <div class="menu-card-actions">
          <label class="stock-toggle" title="${inStock ? 'In Stock' : 'Out of Stock'}">
            <input type="checkbox" data-action="toggle-stock" data-id="${item.id}" ${inStock ? 'checked' : ''} />
            <span class="stock-slider"></span>
            <span class="stock-toggle-label">${inStock ? 'In Stock' : 'Out of Stock'}</span>
          </label>
          <button data-action="edit" data-id="${item.id}" class="menu-action-btn">Edit</button>
          <button data-action="delete" data-id="${item.id}" class="menu-action-btn danger">Delete</button>
        </div>
      </div>
    </div>`;
}

function bindMenuActions(container) {
  container.querySelectorAll('[data-action="edit"]').forEach((btn) => {
    btn.addEventListener('click', () => openMenuItemModal(btn.dataset.id));
  });

  container.querySelectorAll('[data-action="toggle-stock"]').forEach((input) => {
    input.addEventListener('change', async (e) => {
      const id = e.target.dataset.id;
      const inStock = e.target.checked;
      const label = e.target.closest('.stock-toggle')?.querySelector('.stock-toggle-label');
      e.target.disabled = true;

      try {
        await updateDoc(doc(db, 'Hotels', getHotelId(), 'Menu', id), { available: inStock });
        logFirestoreWrite('Menu Stock', `${paths.menuCollection()}/${id}`, { available: inStock });
        toast(inStock ? 'Item is now In Stock on TV' : 'Item hidden from TV menu');
        if (label) label.textContent = inStock ? 'In Stock' : 'Out of Stock';
        e.target.closest('.menu-card')?.classList.toggle('menu-card-out', !inStock);
      } catch (err) {
        e.target.checked = !inStock;
        toast('Failed to update stock status', 'error');
        console.error('[Firestore ERROR] Stock toggle failed:', err);
      } finally {
        e.target.disabled = false;
      }
    });
  });

  container.querySelectorAll('[data-action="delete"]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.id;
      const item = menuItems.find((i) => i.id === id);
      if (!confirm(`Delete "${item?.name || 'this item'}" from the menu?`)) return;

      btn.disabled = true;
      try {
        await deleteDoc(doc(db, 'Hotels', getHotelId(), 'Menu', id));
        logFirestoreWrite('Menu Delete', `${paths.menuCollection()}/${id}`, {});
        toast('Menu item deleted');
      } catch (err) {
        toast('Failed to delete item', 'error');
        console.error('[Firestore ERROR] Delete failed:', err);
        btn.disabled = false;
      }
    });
  });
}

function populateCategorySelect() {
  const select = document.getElementById('menu-item-category');
  if (!select) return;

  select.innerHTML = categories
    .map((c) => `<option value="${escapeHtml(c.key)}">${escapeHtml(c.label)}</option>`)
    .join('');
}

/**
 * Hide Price for corporate properties; Category spans the full row.
 */
function applyMenuPriceFieldVisibility() {
  const corporate = isCorporateProperty();
  const priceWrap = document.getElementById('menu-item-price-wrap');
  const priceInput = document.getElementById('menu-item-price');
  const row = document.getElementById('menu-item-category-price-row');

  if (priceWrap) priceWrap.classList.toggle('hidden', corporate);
  if (priceInput) {
    priceInput.required = !corporate;
    if (corporate) {
      priceInput.value = '0';
      priceInput.removeAttribute('required');
    } else {
      priceInput.setAttribute('required', 'required');
    }
  }
  if (row) {
    row.classList.toggle('grid-cols-2', !corporate);
    row.classList.toggle('grid-cols-1', corporate);
  }
}

function setupMenuItemModal() {
  setupModalClose('menu-item-modal', 'menu-item-close');

  document.getElementById('add-menu-item-btn')?.addEventListener('click', () => {
    openMenuItemModal(null);
  });

  document.getElementById('menu-item-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.textContent = 'Saving…';

    const corporate = isCorporateProperty();
    const payload = {
      name: document.getElementById('menu-item-name').value.trim(),
      description: document.getElementById('menu-item-desc').value.trim(),
      price: corporate
        ? 0
        : parseFloat(document.getElementById('menu-item-price').value) || 0,
      category: document.getElementById('menu-item-category').value,
      imageUrl: document.getElementById('menu-item-image').value.trim(),
      available: document.getElementById('menu-item-available').checked,
      updatedAt: serverTimestamp(),
    };

    if (!payload.name) {
      toast('Dish name is required', 'error');
      btn.disabled = false;
      btn.textContent = 'Save Item';
      return;
    }

    try {
      if (editingItemId) {
        await updateDoc(doc(db, 'Hotels', getHotelId(), 'Menu', editingItemId), payload);
        logFirestoreWrite('Menu Update', `${paths.menuCollection()}/${editingItemId}`, payload);
        toast('Menu item updated — TV synced');
      } else {
        const ref = await addDoc(collection(db, 'Hotels', getHotelId(), 'Menu'), {
          ...payload,
          createdAt: serverTimestamp(),
        });
        logFirestoreWrite('Menu Add', `${paths.menuCollection()}/${ref.id}`, payload);
        toast('Menu item added — live on TV');
      }
      closeModal('menu-item-modal');
      editingItemId = null;
      e.target.reset();
    } catch (err) {
      toast('Failed to save item', 'error');
      console.error('[Firestore ERROR] Menu save failed:', err);
    } finally {
      btn.disabled = false;
      btn.textContent = 'Save Item';
    }
  });
}

function openMenuItemModal(itemId) {
  editingItemId = itemId || null;
  const form = document.getElementById('menu-item-form');
  const title = document.getElementById('menu-item-modal-title');

  populateCategorySelect();
  form?.reset();
  applyMenuPriceFieldVisibility();

  if (itemId) {
    const item = menuItems.find((i) => i.id === itemId);
    if (!item) return;

    if (title) title.textContent = `Edit: ${item.name}`;
    document.getElementById('menu-item-name').value = item.name || '';
    document.getElementById('menu-item-desc').value = item.description || '';
    document.getElementById('menu-item-price').value = isCorporateProperty()
      ? 0
      : item.price || 0;
    document.getElementById('menu-item-category').value = item.category || categories[0]?.key || 'starters';
    document.getElementById('menu-item-image').value = item.imageUrl || '';
    document.getElementById('menu-item-available').checked = item.available !== false;
  } else {
    if (title) title.textContent = 'Add Food Item';
    document.getElementById('menu-item-available').checked = true;
    const defaultCat = activeFilter !== 'all' ? activeFilter : categories[0]?.key || 'starters';
    document.getElementById('menu-item-category').value = defaultCat;
    if (isCorporateProperty()) {
      document.getElementById('menu-item-price').value = '0';
    }
  }

  openModal('menu-item-modal');
  document.getElementById('menu-item-name')?.focus();
}

function setupAddCategoryModal() {
  setupModalClose('add-category-modal', 'add-category-close');

  document.getElementById('add-category-btn')?.addEventListener('click', () => {
    document.getElementById('add-category-form')?.reset();
    openModal('add-category-modal');
    document.getElementById('category-label')?.focus();
  });

  document.getElementById('add-category-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.textContent = 'Adding…';

    const label = document.getElementById('category-label').value.trim();
    const key = slugify(label);

    if (!label || !key) {
      toast('Category name is required', 'error');
      btn.disabled = false;
      btn.textContent = 'Add Category';
      return;
    }

    if (categories.some((c) => c.key === key)) {
      toast('Category already exists', 'error');
      btn.disabled = false;
      btn.textContent = 'Add Category';
      return;
    }

    const updated = [...categories, { key, label }];

    try {
      await setDoc(
        doc(db, 'Hotels', getHotelId(), 'Config', 'menuSettings'),
        { categories: updated, updatedAt: serverTimestamp() },
        { merge: true },
      );
      logFirestoreWrite('Menu Category Add', paths.menuSettingsDoc(), { key, label });
      toast(`Category "${label}" added`);
      closeModal('add-category-modal');
      e.target.reset();
    } catch (err) {
      toast('Failed to add category', 'error');
      console.error('[Firestore ERROR] Category add failed:', err);
    } finally {
      btn.disabled = false;
      btn.textContent = 'Add Category';
    }
  });
}

function slugify(text) {
  return text
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_|_$/g, '');
}

// ── Bulk CSV / XLSX Upload ───────────────────────────────────────────────────

function setupBulkUpload() {
  setupModalClose('bulk-upload-modal', 'bulk-upload-close');

  document.getElementById('bulk-upload-btn')?.addEventListener('click', () => {
    resetBulkUploadUi();
    openModal('bulk-upload-modal');
  });

  document.getElementById('download-sample-csv-btn')?.addEventListener('click', downloadSampleCsv);
  document.getElementById('bulk-download-sample-btn')?.addEventListener('click', downloadSampleCsv);
  document.getElementById('bulk-clear-btn')?.addEventListener('click', resetBulkUploadUi);
  document.getElementById('bulk-import-btn')?.addEventListener('click', () => {
    importBulkRows().catch((err) => {
      console.error('[Bulk Upload] Import failed:', err);
      toast(err.message || 'Import failed', 'error');
      setBulkImporting(false);
    });
  });

  const dropzone = document.getElementById('bulk-dropzone');
  const fileInput = document.getElementById('bulk-file-input');
  if (!dropzone || !fileInput) return;

  dropzone.addEventListener('click', () => {
    if (!bulkImporting) fileInput.click();
  });
  dropzone.addEventListener('keydown', (e) => {
    if ((e.key === 'Enter' || e.key === ' ') && !bulkImporting) {
      e.preventDefault();
      fileInput.click();
    }
  });

  fileInput.addEventListener('change', () => {
    const file = fileInput.files?.[0];
    if (file) handleBulkFile(file);
    fileInput.value = '';
  });

  ['dragenter', 'dragover'].forEach((evt) => {
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropzone.classList.add('is-dragover');
    });
  });
  ['dragleave', 'drop'].forEach((evt) => {
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropzone.classList.remove('is-dragover');
    });
  });
  dropzone.addEventListener('drop', (e) => {
    const file = e.dataTransfer?.files?.[0];
    if (file) handleBulkFile(file);
  });
}

function downloadSampleCsv() {
  const blob = new Blob([SAMPLE_CSV], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'menu_items_sample.csv';
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
  toast('Sample CSV downloaded');
}

function resetBulkUploadUi() {
  bulkParsedRows = [];
  bulkImporting = false;
  setBulkError('');
  const previewWrap = document.getElementById('bulk-preview-wrap');
  const progressWrap = document.getElementById('bulk-progress-wrap');
  const body = document.getElementById('bulk-preview-body');
  const importBtn = document.getElementById('bulk-import-btn');
  if (previewWrap) previewWrap.classList.add('hidden');
  if (progressWrap) progressWrap.classList.add('hidden');
  if (body) body.innerHTML = '';
  if (importBtn) {
    importBtn.disabled = true;
    importBtn.textContent = 'Import to Firestore';
  }
  updateBulkProgress(0, 0);
}

function setBulkError(message) {
  const el = document.getElementById('bulk-error');
  if (!el) return;
  if (!message) {
    el.classList.add('hidden');
    el.textContent = '';
    return;
  }
  el.textContent = message;
  el.classList.remove('hidden');
}

function setBulkImporting(active) {
  bulkImporting = active;
  const importBtn = document.getElementById('bulk-import-btn');
  const clearBtn = document.getElementById('bulk-clear-btn');
  if (importBtn) {
    importBtn.disabled = active || !bulkParsedRows.some((r) => r.valid);
    importBtn.textContent = active ? 'Importing…' : 'Import to Firestore';
  }
  if (clearBtn) clearBtn.disabled = active;
}

async function handleBulkFile(file) {
  if (bulkImporting) return;
  setBulkError('');
  bulkParsedRows = [];

  const name = (file.name || '').toLowerCase();
  const isCsv = name.endsWith('.csv') || file.type === 'text/csv';
  const isXlsx =
    name.endsWith('.xlsx') ||
    name.endsWith('.xls') ||
    file.type.includes('spreadsheet') ||
    file.type.includes('excel');

  if (!isCsv && !isXlsx) {
    setBulkError('Please upload a .csv or .xlsx file.');
    toast('Unsupported file type', 'error');
    return;
  }

  try {
    let rows;
    if (isCsv) {
      rows = await parseCsvFile(file);
    } else {
      rows = await parseXlsxFile(file);
    }
    const { parsed, errors } = validateBulkRows(rows);
    bulkParsedRows = parsed;
    renderBulkPreview(parsed);

    if (errors.length) {
      setBulkError(errors.slice(0, 8).join(' · ') + (errors.length > 8 ? ` (+${errors.length - 8} more)` : ''));
    }

    const validCount = parsed.filter((r) => r.valid).length;
    const importBtn = document.getElementById('bulk-import-btn');
    if (importBtn) {
      importBtn.disabled = validCount === 0;
      importBtn.textContent = validCount
        ? `Import ${validCount} item${validCount === 1 ? '' : 's'} to Firestore`
        : 'Import to Firestore';
    }

    if (!validCount) {
      toast('No valid rows to import', 'error');
    } else {
      toast(`Parsed ${validCount} valid item${validCount === 1 ? '' : 's'}`);
    }
  } catch (err) {
    console.error('[Bulk Upload] Parse failed:', err);
    setBulkError(err.message || 'Failed to parse file');
    toast('Failed to parse file', 'error');
  }
}

function parseCsvFile(file) {
  return new Promise((resolve, reject) => {
    const PapaLib = window.Papa;
    if (!PapaLib) {
      reject(new Error('PapaParse library not loaded'));
      return;
    }
    PapaLib.parse(file, {
      header: true,
      skipEmptyLines: true,
      transformHeader: (h) => normalizeHeader(h),
      complete: (result) => {
        if (result.errors?.length) {
          const fatal = result.errors.filter((e) => e.type === 'Delimiter' || e.type === 'Quotes');
          if (fatal.length) {
            reject(new Error(fatal[0].message || 'CSV parse error'));
            return;
          }
        }
        resolve(result.data || []);
      },
      error: (err) => reject(err || new Error('CSV parse error')),
    });
  });
}

function parseXlsxFile(file) {
  return new Promise((resolve, reject) => {
    const XLSXLib = window.XLSX;
    if (!XLSXLib) {
      reject(new Error('XLSX library not loaded'));
      return;
    }
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const data = new Uint8Array(e.target.result);
        const workbook = XLSXLib.read(data, { type: 'array' });
        const sheetName = workbook.SheetNames[0];
        if (!sheetName) {
          reject(new Error('Workbook has no sheets'));
          return;
        }
        const sheet = workbook.Sheets[sheetName];
        const rows = XLSXLib.utils.sheet_to_json(sheet, { defval: '', raw: false });
        const normalized = rows.map((row) => {
          const out = {};
          Object.keys(row).forEach((key) => {
            out[normalizeHeader(key)] = row[key];
          });
          return out;
        });
        resolve(normalized);
      } catch (err) {
        reject(err);
      }
    };
    reader.onerror = () => reject(new Error('Failed to read Excel file'));
    reader.readAsArrayBuffer(file);
  });
}

function normalizeHeader(header) {
  return String(header || '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '_')
    .replace(/[^a-z0-9_]/g, '');
}

function validateBulkRows(rawRows) {
  const errors = [];
  const parsed = [];

  if (!Array.isArray(rawRows) || !rawRows.length) {
    errors.push('File is empty or has no data rows');
    return { parsed, errors };
  }

  const first = rawRows[0] || {};
  const headers = Object.keys(first);
  const missingCols = REQUIRED_COLUMNS.filter((col) => !headers.includes(col));
  if (missingCols.length) {
    // Also accept if later rows somehow have keys — but typically header row defines keys
    const anyHasRequired = REQUIRED_COLUMNS.every((col) =>
      rawRows.some((r) => Object.prototype.hasOwnProperty.call(r, col)),
    );
    if (!anyHasRequired) {
      errors.push(`Missing required column(s): ${missingCols.join(', ')}`);
      return { parsed, errors };
    }
  }

  rawRows.forEach((row, index) => {
    const rowNum = index + 2; // header is row 1
    const name = String(row.name ?? '').trim();
    const categoryRaw = String(row.category ?? '').trim();
    const category = normalizeCategoryKey(categoryRaw);
    const priceRaw = row.price;
    const price = parsePrice(priceRaw);
    const description = String(row.description ?? '').trim();
    const imageUrl = String(row.image_url ?? row.imageurl ?? row.imageUrl ?? '').trim();
    const isVeg = parseBoolean(row.is_veg ?? row.isveg ?? row.isVeg, true);
    const available = parseBoolean(
      row.is_available ?? row.available ?? row.isavailable,
      true,
    );

    const rowErrors = [];
    if (!name) rowErrors.push('name required');
    if (!categoryRaw) rowErrors.push('category required');
    if (price === null) rowErrors.push(`invalid price "${priceRaw}"`);
    if (price !== null && price < 0) rowErrors.push('price cannot be negative');

    const valid = rowErrors.length === 0;
    if (!valid) {
      errors.push(`Row ${rowNum}: ${rowErrors.join(', ')}`);
    }

    parsed.push({
      rowNum,
      valid,
      error: rowErrors.join(', '),
      payload: {
        name,
        category,
        price: price ?? 0,
        description,
        imageUrl,
        isVeg,
        available,
      },
    });
  });

  return { parsed, errors };
}

function normalizeCategoryKey(raw) {
  const s = String(raw || '').trim().toLowerCase();
  if (!s) return '';
  const aliases = {
    starter: 'starters',
    starters: 'starters',
    'main course': 'main_course',
    maincourse: 'main_course',
    main_course: 'main_course',
    beverage: 'beverages',
    beverages: 'beverages',
    drink: 'beverages',
    drinks: 'beverages',
    dessert: 'desserts',
    desserts: 'desserts',
    breakfast: 'starters',
    snacks: 'starters',
  };
  if (aliases[s]) return aliases[s];
  const slug = slugify(s);
  return aliases[slug] || slug;
}

function parsePrice(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  const cleaned = String(value).replace(/[₹,\s]/g, '').trim();
  if (!cleaned) return null;
  const n = Number(cleaned);
  return Number.isFinite(n) ? n : null;
}

function parseBoolean(value, defaultValue) {
  if (value === null || value === undefined || value === '') return defaultValue;
  if (typeof value === 'boolean') return value;
  const s = String(value).trim().toLowerCase();
  if (['true', '1', 'yes', 'y', 'veg'].includes(s)) return true;
  if (['false', '0', 'no', 'n', 'non-veg', 'nonveg', 'non_veg'].includes(s)) return false;
  return defaultValue;
}

function renderBulkPreview(rows) {
  const wrap = document.getElementById('bulk-preview-wrap');
  const body = document.getElementById('bulk-preview-body');
  const count = document.getElementById('bulk-preview-count');
  if (!wrap || !body) return;

  wrap.classList.remove('hidden');
  if (count) {
    const valid = rows.filter((r) => r.valid).length;
    count.textContent = `${valid}/${rows.length}`;
  }

  body.innerHTML = rows
    .map((row) => {
      const p = row.payload;
      const statusClass = row.valid ? 'bulk-row-ok' : 'bulk-row-bad';
      const statusLabel = row.valid ? 'OK' : escapeHtml(row.error || 'Invalid');
      return `
        <tr class="${statusClass}">
          <td>${row.rowNum}</td>
          <td>${escapeHtml(p.category)}</td>
          <td>${escapeHtml(p.name || '—')}</td>
          <td>${row.valid ? `₹${Number(p.price).toFixed(0)}` : '—'}</td>
          <td>${p.isVeg ? 'Veg' : 'Non-Veg'}</td>
          <td class="bulk-desc-cell">${escapeHtml(p.description || '—')}</td>
          <td>${statusLabel}</td>
        </tr>`;
    })
    .join('');
}

function updateBulkProgress(done, total) {
  const wrap = document.getElementById('bulk-progress-wrap');
  const bar = document.getElementById('bulk-progress-bar');
  const text = document.getElementById('bulk-progress-text');
  const pct = document.getElementById('bulk-progress-pct');
  if (!wrap) return;

  if (!total) {
    wrap.classList.add('hidden');
    if (bar) bar.style.width = '0%';
    if (text) text.textContent = 'Importing…';
    if (pct) pct.textContent = '0%';
    return;
  }

  wrap.classList.remove('hidden');
  const percent = Math.min(100, Math.round((done / total) * 100));
  if (bar) bar.style.width = `${percent}%`;
  if (text) text.textContent = `Importing ${done} of ${total} items…`;
  if (pct) pct.textContent = `${percent}%`;
}

async function importBulkRows() {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No hotel selected', 'error');
    return;
  }

  const validRows = bulkParsedRows.filter((r) => r.valid);
  if (!validRows.length) {
    setBulkError('No valid rows to import.');
    return;
  }

  setBulkImporting(true);
  setBulkError('');
  updateBulkProgress(0, validRows.length);

  try {
    let written = 0;
    for (let i = 0; i < validRows.length; i += BATCH_LIMIT) {
      const chunk = validRows.slice(i, i + BATCH_LIMIT);
      const batch = writeBatch(db);

      chunk.forEach((row) => {
        const ref = doc(collection(db, 'Hotels', hotelId, 'Menu'));
        const payload = {
          name: row.payload.name,
          category: row.payload.category,
          price: Number(row.payload.price),
          description: row.payload.description,
          imageUrl: row.payload.imageUrl,
          isVeg: Boolean(row.payload.isVeg),
          available: row.payload.available !== false,
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
          source: 'bulk_upload',
        };
        batch.set(ref, payload);
      });

      await batch.commit();
      written += chunk.length;
      updateBulkProgress(written, validRows.length);
      logFirestoreWrite(
        'Menu Bulk Import',
        `${paths.menuCollection()} (+${chunk.length})`,
        { count: chunk.length, totalWritten: written },
      );
    }

    toast(`Imported ${written} menu item${written === 1 ? '' : 's'} — live on TV`);
    updateBulkProgress(written, validRows.length);
    const text = document.getElementById('bulk-progress-text');
    if (text) text.textContent = `Done — imported ${written} of ${validRows.length} items`;
    closeModal('bulk-upload-modal');
    resetBulkUploadUi();
  } catch (err) {
    console.error('[Firestore ERROR] Bulk menu import failed:', err);
    setBulkError(err.message || 'Firestore write failed');
    toast('Bulk import failed', 'error');
  } finally {
    setBulkImporting(false);
  }
}
