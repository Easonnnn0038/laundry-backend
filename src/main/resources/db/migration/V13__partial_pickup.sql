-- 历史取衣码原本只保证同一手机号内不重复；清除同门店活动订单中的重复码，应用启动后会重新生成。
UPDATE laundry_order o
JOIN (
  SELECT store_code,pickup_code,MIN(id) keep_id
  FROM laundry_order
  WHERE pickup_code IS NOT NULL AND status IN ('BACK_TO_STORE','NOTIFIED','PARTIALLY_PICKED_UP')
  GROUP BY store_code,pickup_code HAVING COUNT(*)>1
) d ON d.store_code=o.store_code AND d.pickup_code=o.pickup_code AND o.id<>d.keep_id
SET o.pickup_code=NULL,o.update_time=NOW()
WHERE o.status IN ('BACK_TO_STORE','NOTIFIED','PARTIALLY_PICKED_UP');
