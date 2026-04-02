#!/bin/bash
# ResPublika API — Test Suite
BASE="http://localhost:8081"
DEPUTE_ID="PA722336"
DEPUTE_VOTES="PA794038"
SCRUTIN_UID="VTANR5L17V4618"
PASS=0
FAIL=0
ERRORS=""

json_check() {
    local body="$1"
    local expr="$2"
    python3 << PYEOF
import json, sys
try:
    d = json.loads("""$body""")
    result = $expr
    if result is None or result == "" or result == 0 or result is False:
        sys.exit(1)
    print(result)
except Exception as e:
    print(f"ERR: {e}", file=sys.stderr)
    sys.exit(1)
PYEOF
}

test_endpoint() {
    local name="$1"
    local url="$2"
    local expr="$3"

    local response http_code body val
    response=$(curl -s -w "\n%{http_code}" "$url" 2>/dev/null)
    http_code=$(echo "$response" | tail -1)
    body=$(echo "$response" | sed '$d')

    if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
        if [[ -n "$expr" ]]; then
            val=$(json_check "$body" "$expr" 2>/dev/null)
            if [[ $? -eq 0 && -n "$val" ]]; then
                echo "  ✅ $name → $val"
                PASS=$((PASS + 1))
            else
                echo "  ⚠️  $name — CHECK FAILED"
                FAIL=$((FAIL + 1))
                ERRORS="$ERRORS\n  ⚠️  $name\n"
            fi
        else
            echo "  ✅ $name (HTTP $http_code)"
            PASS=$((PASS + 1))
        fi
    else
        echo "  ❌ $name (HTTP $http_code)"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  ❌ $name → HTTP $http_code\n      $(echo "$body" | head -c 200)\n"
    fi
}

test_status() {
    local name="$1"
    local url="$2"
    local expected="$3"

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)
    if [[ "$http_code" == "$expected" ]]; then
        echo "  ✅ $name (HTTP $expected)"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $name — expected $expected got $http_code"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  ❌ $name → expected $expected, got $http_code\n"
    fi
}

echo "╔══════════════════════════════════════════════════════╗"
echo "║         🧪 ResPublika API Test Suite                ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

echo "── Infrastructure ──"
test_status "GET /" "$BASE/" "200"
test_status "GET /swagger" "$BASE/swagger" "200"

echo ""
echo "── Députés ──"
test_endpoint "Liste complète" \
    "$BASE/api/deputes" \
    'd["total"]'

test_endpoint "Filtre par nom" \
    "$BASE/api/deputes?nom=David" \
    'f"{d[\"total\"]} deputes found"'

test_endpoint "Filtre par groupe" \
    "$BASE/api/deputes?groupe=PO845470" \
    'f"{d[\"total\"]} in groupe"'

test_endpoint "Pagination" \
    "$BASE/api/deputes?page=2&limit=5" \
    'f"{len(d[\"deputes\"])} deputes on page {d[\"page\"]}"'

test_endpoint "Profil député" \
    "$BASE/api/deputes/$DEPUTE_ID" \
    'd["nom"]'

test_endpoint "Stats votes député" \
    "$BASE/api/deputes/$DEPUTE_ID" \
    'f"total={d[\"stats_votes\"][\"total\"]} pour={d[\"stats_votes\"][\"pour\"]}"'

test_status "Député inexistant → 404" \
    "$BASE/api/deputes/FAKE_999" "404"

echo ""
echo "── Votes Députés ──"
test_endpoint "Historique de vote" \
    "$BASE/api/deputes/$DEPUTE_VOTES/votes" \
    'f"{d[\"total\"]} votes"'

test_endpoint "Historique paginé (limit=5)" \
    "$BASE/api/deputes/$DEPUTE_VOTES/votes?page=1&limit=5" \
    'f"{len(d[\"votes\"])} votes returned"'

test_status "Votes député inexistant → 404" \
    "$BASE/api/deputes/FAKE_999/votes" "404"

echo ""
echo "── Dissidences ──"
test_endpoint "Top dissidences" \
    "$BASE/api/deputes/$DEPUTE_ID/top-dissidences" \
    'f"{d[\"nb_dissidences\"]} dissidences"'

test_status "Dissidences député inexistant → 404" \
    "$BASE/api/deputes/FAKE_999/top-dissidences" "404"

echo ""
echo "── Scrutins ──"
test_endpoint "Scrutin détail complet" \
    "$BASE/api/scrutins/$SCRUTIN_UID/full" \
    'd["titre"][:80]'

test_endpoint "Scrutin — groupes présents" \
    "$BASE/api/scrutins/$SCRUTIN_UID/full" \
    'f"{len(d[\"groupes\"])} groupes"'

test_endpoint "Scrutin — synthèse textuelle" \
    "$BASE/api/scrutins/$SCRUTIN_UID/full" \
    'd["groupes"][0]["synthese"]'

test_status "Scrutin inexistant → 404" \
    "$BASE/api/scrutins/FAKE_SCRUTIN/full" "404"

test_endpoint "Recherche motion" \
    "$BASE/api/scrutins/recherche?q=motion" \
    'f"{d[\"total\"]} scrutins found"'

echo ""
echo "── Éthique ──"
test_endpoint "Derniers déports" \
    "$BASE/api/deports/latest" \
    'f"{d[\"count\"]} deports"'

test_endpoint "Déports député + badge" \
    "$BASE/api/deputes/$DEPUTE_ID/deports" \
    'f"badge={d[\"badge_ethique_plus\"]} ({d[\"nb_deports\"]} deports)" if isinstance(d.get("badge_ethique_plus"), bool) else None'

test_status "Déports député inexistant → 404" \
    "$BASE/api/deputes/FAKE_999/deports" "404"

# --- Report ---
TOTAL=$((PASS + FAIL))
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║                   📊 RAPPORT                        ║"
echo "╠══════════════════════════════════════════════════════╣"
printf "║  Total: %-3d │ ✅ Pass: %-3d │ ❌ Fail: %-3d        ║\n" $TOTAL $PASS $FAIL
echo "╚══════════════════════════════════════════════════════╝"

if [[ $FAIL -gt 0 ]]; then
    echo ""
    echo "📋 DÉTAIL DES ERREURS :"
    echo -e "$ERRORS"
    echo "⚠️  $FAIL test(s) en échec."
else
    echo ""
    echo "🏆 Tous les tests passent."
fi
