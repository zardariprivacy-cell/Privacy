// ShopNest — JavaScript the course way: data → DOM rendering → events → CRUD cart

const ICONS = {
  headphones: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M4 15v-2a8 8 0 0 1 16 0v2"/><rect x="3" y="14" width="4.5" height="7" rx="2"/><rect x="16.5" y="14" width="4.5" height="7" rx="2"/></svg>',
  watch: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><rect x="7" y="6" width="10" height="12" rx="3"/><path d="M9.5 6l.7-3h3.6l.7 3M9.5 18l.7 3h3.6l.7-3M12 9.5V12l1.8 1.2"/></svg>',
  shoe: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 16.5V18a1.5 1.5 0 0 0 1.5 1.5h15A1.5 1.5 0 0 0 21 18v-1c0-1.2-1-2-2.2-2.3C15.5 14 13 12.4 10.8 9.7L9.4 8 6 11.2c-1 .9-3 1.6-3 3.3v2z"/><path d="M6.5 12.5l1.8 1M9.3 10.3l1.8 1"/></svg>',
  backpack: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M6 20V10a6 6 0 0 1 12 0v10a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 20z"/><path d="M9 21v-5a3 3 0 0 1 6 0v5M9.5 7.5h5"/></svg>',
  glasses: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><circle cx="6.5" cy="14" r="3.2"/><circle cx="17.5" cy="14" r="3.2"/><path d="M9.7 14h4.6M3.3 14L2 6.5M20.7 14L22 6.5"/></svg>',
  camera: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><rect x="3" y="7" width="18" height="13" rx="2.5"/><circle cx="12" cy="13.5" r="3.6"/><path d="M8.5 7l1.2-2.5h4.6L15.5 7"/></svg>',
  keyboard: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><rect x="2.5" y="6" width="19" height="12" rx="2"/><path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M6 14h.01M18 14h.01M9 14h6"/></svg>',
  lamp: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M8 3h8l3.5 8h-15L8 3z"/><path d="M12 11v7M8.5 21h7M12 18v3"/></svg>',
  ball: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7l3 2.2-1.1 3.5h-3.8L9 9.2 12 7zM12 3v4M18.5 6.5L15 9.2M20.5 14.5l-3.9-1.8M15.5 20.4l-1.6-3.7M8.5 20.4l1.6-3.7M3.5 14.5l3.9-1.8M5.5 6.5L9 9.2"/></svg>',
  book: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M5 3.5h13A1.5 1.5 0 0 1 19.5 5v14a1.5 1.5 0 0 1-1.5 1.5H5z"/><path d="M5 3.5V20M9 8h6"/></svg>',
  shirt: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M8.5 4L3 7.5 5 12l2-1v9h10v-9l2 1 2-4.5L15.5 4a3.5 3.5 0 0 1-7 0z"/></svg>',
  sofa: '<svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M5 11V8.5A2.5 2.5 0 0 1 7.5 6h9A2.5 2.5 0 0 1 19 8.5V11"/><path d="M3 13.5A1.5 1.5 0 0 1 4.5 12h15A1.5 1.5 0 0 1 21 13.5V17a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17v-3.5z"/><path d="M5 21v-2.5M19 21v-2.5"/></svg>'
};

const CATEGORIES = [
  { name: "Electronics", icon: "headphones", tile: "linear-gradient(135deg,#4361ee,#7209b7)" },
  { name: "Fashion", icon: "shirt", tile: "linear-gradient(135deg,#f72585,#ff8c42)" },
  { name: "Home & Living", icon: "sofa", tile: "linear-gradient(135deg,#2a9d8f,#57cc99)" },
  { name: "Sports", icon: "ball", tile: "linear-gradient(135deg,#fb8500,#ffb703)" },
  { name: "Watches", icon: "watch", tile: "linear-gradient(135deg,#3a506b,#6fffe9)" },
  { name: "Cameras", icon: "camera", tile: "linear-gradient(135deg,#5a189a,#9d4edd)" },
  { name: "Books", icon: "book", tile: "linear-gradient(135deg,#bc6c25,#dda15e)" },
  { name: "Accessories", icon: "glasses", tile: "linear-gradient(135deg,#0aa6a6,#48cae4)" }
];

const PRODUCTS = [
  { id: 1, name: "Aurora Wireless Headphones", cat: "Electronics", price: 59.99, old: 89.99, rating: 4.5, icon: "headphones", tile: "linear-gradient(135deg,#4361ee,#7209b7)" },
  { id: 2, name: "Pulse Smart Watch Series 5", cat: "Watches", price: 129.00, old: 169.00, rating: 4.8, icon: "watch", tile: "linear-gradient(135deg,#3a506b,#6fffe9)" },
  { id: 3, name: "Runner Flex Sneakers", cat: "Fashion", price: 74.50, old: null, rating: 4.3, icon: "shoe", tile: "linear-gradient(135deg,#f72585,#ff8c42)" },
  { id: 4, name: "Voyager Travel Backpack", cat: "Accessories", price: 45.00, old: 65.00, rating: 4.6, icon: "backpack", tile: "linear-gradient(135deg,#0aa6a6,#48cae4)" },
  { id: 5, name: "Solstice Sunglasses", cat: "Accessories", price: 25.99, old: null, rating: 4.1, icon: "glasses", tile: "linear-gradient(135deg,#fb8500,#ffb703)" },
  { id: 6, name: "Optix Pro Mirrorless Camera", cat: "Cameras", price: 649.00, old: 799.00, rating: 4.9, icon: "camera", tile: "linear-gradient(135deg,#5a189a,#9d4edd)" },
  { id: 7, name: "Tactile Mechanical Keyboard", cat: "Electronics", price: 89.00, old: 119.00, rating: 4.7, icon: "keyboard", tile: "linear-gradient(135deg,#2a9d8f,#57cc99)" },
  { id: 8, name: "Halo Minimal Desk Lamp", cat: "Home & Living", price: 39.95, old: null, rating: 4.4, icon: "lamp", tile: "linear-gradient(135deg,#bc6c25,#dda15e)" }
];

const productGrid = document.getElementById("productGrid");
const categoryGrid = document.getElementById("categoryGrid");
const noResults = document.getElementById("noResults");
const cart = new Map(); // id -> quantity (CRUD: create/read/update/delete)

function stars(r) {
  const full = Math.round(r);
  return "★".repeat(full) + "☆".repeat(5 - full) + " <span>(" + r + ")</span>";
}

function renderCategories() {
  categoryGrid.innerHTML = CATEGORIES.map(c => `
    <div class="category-card" data-cat="${c.name}">
      <div class="category-tile" style="background:${c.tile}">${ICONS[c.icon]}</div>
      <h3>${c.name}</h3><p>Shop now →</p>
    </div>`).join("");
  categoryGrid.querySelectorAll(".category-card").forEach(card => {
    card.addEventListener("click", () => {
      document.getElementById("searchInput").value = card.dataset.cat;
      renderProducts(card.dataset.cat);
      document.getElementById("products").scrollIntoView({ behavior: "smooth" });
    });
  });
}

function renderProducts(filter = "") {
  const q = filter.trim().toLowerCase();
  const list = PRODUCTS.filter(p => !q || p.name.toLowerCase().includes(q) || p.cat.toLowerCase().includes(q));
  noResults.hidden = list.length > 0;
  productGrid.innerHTML = list.map(p => `
    <article class="product-card">
      <div class="product-tile" style="background:${p.tile}">${ICONS[p.icon]}</div>
      <div class="product-body">
        <p class="product-cat">${p.cat}</p>
        <h3>${p.name}</h3>
        <p class="product-rating">${stars(p.rating)}</p>
        <div class="price-row"><span class="price">$${p.price.toFixed(2)}</span>${p.old ? `<span class="old-price">$${p.old.toFixed(2)}</span>` : ""}</div>
        <button class="add-btn" data-add="${p.id}">Add to cart</button>
      </div>
    </article>`).join("");
  productGrid.querySelectorAll("[data-add]").forEach(btn => {
    btn.addEventListener("click", () => addToCart(Number(btn.dataset.add)));
  });
}

// ---- Cart CRUD ----
const cartDrawer = document.getElementById("cartDrawer");
const cartOverlay = document.getElementById("cartOverlay");
const cartItems = document.getElementById("cartItems");
const cartCount = document.getElementById("cartCount");
const cartTotal = document.getElementById("cartTotal");

function addToCart(id) {
  cart.set(id, (cart.get(id) || 0) + 1);
  renderCart();
  openCart();
}
function removeFromCart(id) { cart.delete(id); renderCart(); }

function renderCart() {
  let count = 0, total = 0;
  if (cart.size === 0) {
    cartItems.innerHTML = '<p class="cart-empty">Your cart is empty.</p>';
  } else {
    cartItems.innerHTML = [...cart.entries()].map(([id, qty]) => {
      const p = PRODUCTS.find(x => x.id === id);
      count += qty; total += p.price * qty;
      return `<div class="cart-item">
        <div class="cart-item-tile" style="background:${p.tile}">${ICONS[p.icon].replace('width="52" height="52"', 'width="26" height="26"')}</div>
        <div class="cart-item-info"><h4>${p.name}</h4><p>$${p.price.toFixed(2)} × ${qty}</p></div>
        <button class="remove-btn" data-remove="${id}">Remove</button>
      </div>`;
    }).join("");
    cartItems.querySelectorAll("[data-remove]").forEach(b =>
      b.addEventListener("click", () => removeFromCart(Number(b.dataset.remove))));
  }
  cartCount.textContent = count;
  cartTotal.textContent = "$" + total.toFixed(2);
}

function openCart() { cartDrawer.classList.add("open"); cartOverlay.hidden = false; }
function closeCart() { cartDrawer.classList.remove("open"); cartOverlay.hidden = true; }
document.getElementById("cartBtn").addEventListener("click", openCart);
document.getElementById("cartClose").addEventListener("click", closeCart);
cartOverlay.addEventListener("click", closeCart);
document.getElementById("checkoutBtn").addEventListener("click", function () {
  this.textContent = "Demo store — no real orders ✓";
  setTimeout(() => this.textContent = "Checkout (demo)", 2200);
});

// ---- Search (course: forms + DOM events) ----
const searchInput = document.getElementById("searchInput");
searchInput.addEventListener("input", () => renderProducts(searchInput.value));
document.getElementById("searchForm").addEventListener("submit", e => {
  e.preventDefault();
  renderProducts(searchInput.value);
  document.getElementById("products").scrollIntoView({ behavior: "smooth" });
});

// ---- Newsletter validation ----
document.getElementById("newsletterForm").addEventListener("submit", e => {
  e.preventDefault();
  const email = document.getElementById("newsletterEmail").value.trim();
  const msg = document.getElementById("formMsg");
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    msg.textContent = "Subscribed! Welcome to ShopNest offers.";
    msg.className = "form-msg ok";
    e.target.reset();
  } else {
    msg.textContent = "Please enter a valid email address.";
    msg.className = "form-msg err";
  }
});

renderCategories();
renderProducts();
renderCart();
