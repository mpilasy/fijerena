#!/usr/bin/env bash
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
   echo "Error: This script must be run as root (use: sudo bash $0)" >&2
   exit 1
fi

echo "==> Configuring zramswap..."
cat << 'EOF' > /etc/default/zramswap
PERCENT=50
ALGO=lz4
PRIORITY=100
EOF

echo "==> Configuring sysctl (swappiness=10, vfs_cache_pressure=50)..."
cat << 'EOF' > /etc/sysctl.d/99-fijerena-vm.conf
vm.swappiness = 10
vm.vfs_cache_pressure = 50
EOF

echo "==> Applying sysctl settings..."
sysctl --system > /dev/null

echo "==> Re-enabling standard swap..."
swapon -a

echo "==> Starting zramswap service..."
systemctl restart zramswap

echo "==> Done! Current status:"
echo "--- Swappiness ---"
cat /proc/sys/vm/swappiness
echo "--- Active Swap Devices ---"
swapon --show
