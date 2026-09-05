# Hostity marketing site — go.hostity.in

Premium public landing for the Hostity hotel & corporate TV platform.

- **Admin panel:** `www.hostity.in` (unchanged)
- **This showcase:** `go.hostity.in`
- **Future brand:** `pcncloud.in`

## Local preview

```bash
cd go-site
npx --yes serve . -p 4173
```

Open http://localhost:4173

## Deploy on Vercel

1. Vercel → **Add New Project** → this repo  
2. **Root Directory:** `go-site`  
3. Framework preset: **Other** (static)  
4. Domains → add **`go.hostity.in`**  
5. DNS: `CNAME` name `go` → `cname.vercel-dns.com` (or value Vercel shows)

Keep the admin project bound only to `www.hostity.in`.
