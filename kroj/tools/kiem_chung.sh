cd /tmp/krorun
echo "# Kiểm chứng CHROISEN_LGT_vi1.jar ($(date -u +%F)) — class + tài nguyên lấy từ jar, harness RunHeadless"
echo "jar md5: $(md5sum /home/user/wipi-port-j2me/dist/CHROISEN_LGT_vi1.jar | cut -c1-32)"; echo
echo "## AOT vs thông dịch (300 s, cùng kịch bản phím)"
grep -h -E "^instructions" fI.log fA.log | sed 's/^/  /'
cmp -s fI/svclog.txt fA/svclog.txt && echo "  svclog: TRÙNG ($(grep '^H ' fI/svclog.txt | tail -1 | cut -d' ' -f2)+ lần gọi host)" || echo "  svclog: KHÁC"
n=0; d=0; for x in fI/f*.png; do n=$((n+1)); cmp -s $x fA/${x#fI/} || d=$((d+1)); done; echo "  khung hình: $((n-d))/$n trùng"; echo
echo "## Mua cash -> tự lưu -> khởi động lại -> mở túi"
grep -a -h "netConnect\|netSocketWrite\|netSocketRead" fbuy.log | sed 's/^/  /'
echo "  save0.dat đổi: $(cmp -l /home/user/wipi-port-j2me/kro/test/save0.dat fbuy/storage/save0.dat | wc -l) byte; lúc tải lại: netConnect = $(grep -ac netConnect fload.log)"; echo
echo "## Chơi ngẫu nhiên 700 s (AOT)"
grep -h -E "^instructions|^heap" fmonkey.log | sed 's/^/  /'
echo "  yêu cầu mạng bị từ chối như mất mạng: $(grep -ac 'netConnect -> fail' fmonkey.log)"; echo
echo "## Exception / hàm WIPI chưa cài"
for f in fI fA fbuy fload fmonkey; do echo "  $f: $(grep -ciE 'exception|unimplemented' $f.log)"; done
