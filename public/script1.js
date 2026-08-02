document.addEventListener('DOMContentLoaded', () => {

    const BASE_URL = "http://localhost:9090/api/restaurant";

    const navButtons = document.querySelectorAll('.nav-btn');
    const views = document.querySelectorAll('.view');
    const pageTitle = document.getElementById('page-title');

    // ===== TOAST =====
    function showToast(msg, isError = false) {
        let toast = document.getElementById('toast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'toast';
            document.body.appendChild(toast);
        }
        toast.textContent = msg;
        toast.className = 'show' + (isError ? ' error' : '');
        clearTimeout(toast._timer);
        toast._timer = setTimeout(() => { toast.className = ''; }, 3000);
    }

    // ===== NAVIGATION =====
    navButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const target = btn.getAttribute('data-target');

            navButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            views.forEach(v => v.classList.remove('active'));
            document.getElementById(target).classList.add('active');

            pageTitle.textContent = btn.textContent.replace(/[^\w\s]/g, '').trim();

            if (target === 'dashboard') {
                loadDashboard();
            } else {
                fetchData();
            }
        });
    });

    // ===== DASHBOARD STATS =====
    async function loadDashboard() {
        try {
            const [customers, orders, staff, deliveries] = await Promise.all([
                fetch(`${BASE_URL}/customers`).then(r => r.json()),
                fetch(`${BASE_URL}/orders`).then(r => r.json()),
                fetch(`${BASE_URL}/staff`).then(r => r.json()),
                fetch(`${BASE_URL}/deliveries`).then(r => r.json())
            ]);

            const sc = document.getElementById('stat-customers');
            const so = document.getElementById('stat-orders');
            const ss = document.getElementById('stat-staff');
            const sd = document.getElementById('stat-deliveries');

            if (sc) sc.textContent = customers.length;
            if (so) so.textContent = orders.length;
            if (ss) ss.textContent = staff.length;
            if (sd) sd.textContent = deliveries.length;

        } catch (err) {
            console.error("Dashboard fetch error:", err);
            showToast("Cannot connect to server.", true);
        }
    }

    // ===== FETCH TABLE DATA =====
    async function fetchData() {
        const activeViewEl = document.querySelector('.view.active');
        if (!activeViewEl) return;
        const activeView = activeViewEl.id;

        if (activeView === 'dashboard') {
            loadDashboard();
            return;
        }

        try {
            const res = await fetch(`${BASE_URL}/${activeView}`);
            const data = await res.json();

            if (activeView === "customers")       renderCustomers(data);
            else if (activeView === "orders")     renderOrders(data);
            else if (activeView === "staff")      renderStaff(data);
            else if (activeView === "deliveries") renderDeliveries(data);

        } catch (err) {
            console.error("Fetch error:", err);
            showToast("Cannot connect to server.", true);
        }
    }

    // ===== DELETE FUNCTION =====
    async function deleteRecord(endpoint, id) {
        if (!confirm(`Are you sure you want to delete record #${id}?`)) return;

        try {
            const res = await fetch(`${BASE_URL}/${endpoint}/${id}`, {
                method: "DELETE"
            });
            const text = await res.text();
            showToast(text, text.toLowerCase().includes('not found'));
            fetchData(); // Refresh table after delete
        } catch (err) {
            console.error("Delete error:", err);
            showToast("Cannot connect to server.", true);
        }
    }

    // Make deleteRecord available globally so onclick in HTML works
    window.deleteRecord = deleteRecord;

    // ===== COMMON POST =====
    async function postData(endpoint, data, clearFn) {
        for (let key in data) {
            const val = data[key];
            if (val === null || val === undefined || val.toString().trim() === "") {
                showToast(`Please fill in all fields! Missing: ${key}`, true);
                return;
            }
        }

        try {
            const res = await fetch(`${BASE_URL}/${endpoint}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(data)
            });

            const text = await res.text();
            const isError = text.toLowerCase().includes('error') || text.toLowerCase().includes('duplicate');
            showToast(text || "Inserted successfully", isError);

            if (!isError) {
                if (clearFn) clearFn();
                fetchData();
            }

        } catch (err) {
            console.error("Post error:", err);
            showToast("Cannot connect to server.", true);
        }
    }

    // ===== HELPER: clear inputs =====
    function clearFields(...ids) {
        ids.forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
    }

    // ===== ADD FUNCTIONS =====
    window.addCustomer = () => postData(
        "customers",
        {
            customer_id: document.getElementById("customer_id").value.trim(),
            name:        document.getElementById("customer_name").value.trim(),
            phone:       document.getElementById("customer_phone").value.trim(),
            address:     document.getElementById("customer_address").value.trim()
        },
        () => clearFields("customer_id", "customer_name", "customer_phone", "customer_address")
    );

    window.addOrder = () => postData(
        "orders",
        {
            order_id:    document.getElementById("order_id").value.trim(),
            customer_id: document.getElementById("order_customer_id").value.trim(),
            order_date:  document.getElementById("order_date").value.trim(),
            status:      document.getElementById("order_status").value.trim()
        },
        () => {
            clearFields("order_id", "order_customer_id", "order_date");
            document.getElementById("order_status").value = "";
        }
    );

    window.addStaff = () => postData(
        "staff",
        {
            staff_id: document.getElementById("staff_id").value.trim(),
            name:     document.getElementById("staff_name").value.trim(),
            phone:    document.getElementById("staff_phone").value.trim()
        },
        () => clearFields("staff_id", "staff_name", "staff_phone")
    );

    window.addDelivery = () => postData(
        "deliveries",
        {
            delivery_id:   document.getElementById("delivery_id").value.trim(),
            order_id:      document.getElementById("delivery_order_id").value.trim(),
            staff_id:      document.getElementById("delivery_staff_id").value.trim(),
            delivery_time: document.getElementById("delivery_time").value.trim(),
            delivery_date: document.getElementById("delivery_date").value.trim()
        },
        () => clearFields("delivery_id", "delivery_order_id", "delivery_staff_id", "delivery_time", "delivery_date")
    );

    // ===== RENDER FUNCTIONS (with Delete button) =====
    function renderCustomers(data) {
        const tbody = document.getElementById("customers-body");
        if (!tbody) return;
        tbody.innerHTML = data.length
            ? data.map(c => `
                <tr>
                    <td>${c.customer_id}</td>
                    <td>${c.name}</td>
                    <td>${c.phone}</td>
                    <td>${c.address}</td>
                    <td>
                        <button class="delete-btn"
                            onclick="deleteRecord('customers', ${c.customer_id})">
                            🗑 Delete
                        </button>
                    </td>
                </tr>`).join('')
            : '<tr class="empty-row"><td colspan="5">No customers found</td></tr>';
    }

    function renderOrders(data) {
        const tbody = document.getElementById("orders-body");
        if (!tbody) return;
        tbody.innerHTML = data.length
            ? data.map(o => `
                <tr>
                    <td>${o.order_id}</td>
                    <td>${o.customer_id}</td>
                    <td>${o.order_date}</td>
                    <td>
                        <span style="padding:2px 10px;border-radius:20px;font-size:.8rem;
                        background:${o.status === 'Delivered' ? '#dcfce7' : '#fef9c3'};
                        color:${o.status === 'Delivered' ? '#16a34a' : '#854d0e'}">
                            ${o.status}
                        </span>
                    </td>
                    <td>
                        <button class="delete-btn"
                            onclick="deleteRecord('orders', ${o.order_id})">
                            🗑 Delete
                        </button>
                    </td>
                </tr>`).join('')
            : '<tr class="empty-row"><td colspan="5">No orders found</td></tr>';
    }

    function renderStaff(data) {
        const tbody = document.getElementById("staff-body");
        if (!tbody) return;
        tbody.innerHTML = data.length
            ? data.map(s => `
                <tr>
                    <td>${s.staff_id}</td>
                    <td>${s.name}</td>
                    <td>${s.phone}</td>
                    <td>
                        <button class="delete-btn"
                            onclick="deleteRecord('staff', ${s.staff_id})">
                            🗑 Delete
                        </button>
                    </td>
                </tr>`).join('')
            : '<tr class="empty-row"><td colspan="4">No staff found</td></tr>';
    }

    function renderDeliveries(data) {
        const tbody = document.getElementById("delivery-body");
        if (!tbody) return;
        tbody.innerHTML = data.length
            ? data.map(d => `
                <tr>
                    <td>${d.delivery_id}</td>
                    <td>${d.order_id}</td>
                    <td>${d.staff_id}</td>
                    <td>${d.delivery_time}</td>
                    <td>${d.delivery_date}</td>
                    <td>
                        <button class="delete-btn"
                            onclick="deleteRecord('deliveries', ${d.delivery_id})">
                            🗑 Delete
                        </button>
                    </td>
                </tr>`).join('')
            : '<tr class="empty-row"><td colspan="6">No deliveries found</td></tr>';
    }

    // ===== INITIAL LOAD =====
    loadDashboard();

});