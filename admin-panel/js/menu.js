import { db, HOTEL_ID } from './firebase-config.js';
import {
  collection,
  doc,
  addDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  onSnapshot,
  serverTimestamp,
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

export function initMenu() {
  renderFilterTabs();
  setupMenuItemModal();
  setupAddCategoryModal();
  listenMenuSettings();
  listenMenu();
}

function listenMenuSettings() {
  const docPath = paths.menuSettingsDoc();
  logFirestoreListen('Menu Settings', docPath);

  onSnapshot(
    doc(db, 'Hotels', HOTEL_ID, 'Config', 'menuSettings'),
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
  try {
    const payload = {
      categories: DEFAULT_CATEGORIES,
      updatedAt: serverTimestamp(),
    };
    await setDoc(doc(db, 'Hotels', HOTEL_ID, 'Config', 'menuSettings'), payload, { merge: true });
    logFirestoreWrite('Menu Settings Seed', paths.menuSettingsDoc(), payload);
  } catch (err) {
    console.error('[Firestore ERROR] Menu settings seed failed:', err);
  }
}

function listenMenu() {
  logFirestoreListen('Menu', paths.menuCollection());

  onSnapshot(
    collection(db, 'Hotels', HOTEL_ID, 'Menu'),
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
        await updateDoc(doc(db, 'Hotels', HOTEL_ID, 'Menu', id), { available: inStock });
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
        await deleteDoc(doc(db, 'Hotels', HOTEL_ID, 'Menu', id));
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

    const payload = {
      name: document.getElementById('menu-item-name').value.trim(),
      description: document.getElementById('menu-item-desc').value.trim(),
      price: parseFloat(document.getElementById('menu-item-price').value) || 0,
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
        await updateDoc(doc(db, 'Hotels', HOTEL_ID, 'Menu', editingItemId), payload);
        logFirestoreWrite('Menu Update', `${paths.menuCollection()}/${editingItemId}`, payload);
        toast('Menu item updated — TV synced');
      } else {
        const ref = await addDoc(collection(db, 'Hotels', HOTEL_ID, 'Menu'), {
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

  if (itemId) {
    const item = menuItems.find((i) => i.id === itemId);
    if (!item) return;

    if (title) title.textContent = `Edit: ${item.name}`;
    document.getElementById('menu-item-name').value = item.name || '';
    document.getElementById('menu-item-desc').value = item.description || '';
    document.getElementById('menu-item-price').value = item.price || 0;
    document.getElementById('menu-item-category').value = item.category || categories[0]?.key || 'starters';
    document.getElementById('menu-item-image').value = item.imageUrl || '';
    document.getElementById('menu-item-available').checked = item.available !== false;
  } else {
    if (title) title.textContent = 'Add Food Item';
    document.getElementById('menu-item-available').checked = true;
    const defaultCat = activeFilter !== 'all' ? activeFilter : categories[0]?.key || 'starters';
    document.getElementById('menu-item-category').value = defaultCat;
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
        doc(db, 'Hotels', HOTEL_ID, 'Config', 'menuSettings'),
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
