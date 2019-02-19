#!/bin/bash
cd "$(dirname "$0")"
cd ../WD
cat  > ub <<EOL
#!/bin/bash
java -jar $(readlink -f uartbus.jar) \$@
EOL
chmod 755 ub
echo "Copy this into the terminal: export PATH=\$PATH:$(readlink -f .)"
